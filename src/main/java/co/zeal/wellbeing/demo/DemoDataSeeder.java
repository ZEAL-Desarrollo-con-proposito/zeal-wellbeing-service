package co.zeal.wellbeing.demo;

import co.zeal.wellbeing.reservation.Reservation;
import co.zeal.wellbeing.reservation.ReservationRepository;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Agenda de demostración relativa al día de arranque, para que la sustentación muestre cada
 * estado sin preparación manual. Solo corre si no hay franjas (no pisa datos reales) y se apaga
 * con {@code app.demo-data.enabled=false}.
 *
 * <ul>
 *   <li>Laura (usr-psicologo-001): bloques libres cada día y una sesión con David que empieza en
 *       pocos minutos (sala, WebRTC y chat).</li>
 *   <li>En dos días hábiles: David a las 9:00 y Mariana a las 15:00 con Laura. Si David cancela,
 *       Mariana recibe el cupo liberado (reto C-3).</li>
 *   <li>Andrés tiene bloques libres, Valeria la semana llena y Camila no ha abierto agenda.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final Duration SESSION = Duration.ofMinutes(50);
    private static final int DAYS = 12;

    private static final String LAURA = "usr-psicologo-001";
    private static final String ANDRES = "usr-psicologo-002";
    private static final String VALERIA = "usr-psicologo-003";

    private static final Employee DAVID = new Employee("usr-empleado-001", "David Cajamarca");
    private static final Employee MARIANA = new Employee("usr-empleado-002", "Mariana Torres");
    private static final List<Employee> OTHERS = List.of(
            new Employee("usr-empleado-003", "Camilo Rincón"),
            new Employee("usr-empleado-004", "Sofía Mejía"),
            new Employee("usr-empleado-005", "Juan Pérez"),
            new Employee("usr-empleado-006", "Valentina Arias"));

    private final SlotRepository slots;
    private final ReservationRepository reservations;
    private final Clock clock;
    private final ZoneId zone;

    public DemoDataSeeder(SlotRepository slots, ReservationRepository reservations, Clock clock,
                          ZoneId businessZone) {
        this.slots = slots;
        this.reservations = reservations;
        this.clock = clock;
        this.zone = businessZone;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (slots.count() > 0) {
            log.info("[demo] Ya existen franjas; no se siembran datos de demostración.");
            return;
        }
        Instant now = Instant.now(clock);
        List<Booking> bookings = new ArrayList<>();
        Instant demoStart = now.truncatedTo(ChronoUnit.MINUTES).plus(Duration.ofMinutes(5));
        bookings.add(new Booking(LAURA, demoStart, DAVID));

        List<LocalDate> workdays = workdaysFrom(LocalDate.ofInstant(now, zone));
        LocalDate cancellationDemoDay = workdays.stream()
                .filter(day -> day.atStartOfDay(zone).toInstant().isAfter(now.plus(Duration.ofHours(30))))
                .findFirst()
                .orElseThrow();
        int other = 0;
        for (LocalDate day : workdays) {
            boolean firstWeek = day.isBefore(workdays.getFirst().plusDays(7));
            for (int hour : List.of(8, 9, 10, 11, 14, 15, 16)) {
                Employee employee = null;
                if (day.equals(cancellationDemoDay) && hour == 9) {
                    employee = DAVID;
                } else if (day.equals(cancellationDemoDay) && hour == 15) {
                    employee = MARIANA;
                } else if (hour == 11) {
                    employee = OTHERS.get(other++ % OTHERS.size());
                }
                bookings.add(new Booking(LAURA, at(day, hour), employee));
            }
            for (int hour : List.of(8, 13, 17)) {
                bookings.add(new Booking(ANDRES, at(day, hour),
                        firstWeek && hour == 8 ? OTHERS.get(other++ % OTHERS.size()) : null));
            }
            for (int hour : List.of(9, 11, 15)) {
                bookings.add(new Booking(VALERIA, at(day, hour), firstWeek ? OTHERS.get(other++ % OTHERS.size()) : null));
            }
        }

        List<Booking> accepted = new ArrayList<>();
        for (Booking booking : bookings) {
            boolean future = booking.startsAt().isAfter(now);
            boolean clashes = accepted.stream().anyMatch(previous -> previous.overlaps(booking));
            if (future && !clashes) {
                accepted.add(booking);
            }
        }
        for (Booking booking : accepted) {
            Slot slot = slots.save(Slot.open(booking.psychologistId(), booking.startsAt(),
                    booking.startsAt().plus(SESSION)));
            if (booking.employee() != null) {
                reservations.save(Reservation.confirm(slot, booking.employee().id(), booking.employee().fullName(),
                        now));
            }
        }
        log.info("[demo] Sembradas {} franjas de demostración.", accepted.size());
    }

    private List<LocalDate> workdaysFrom(LocalDate today) {
        return today.datesUntil(today.plusDays(DAYS))
                .filter(day -> day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY)
                .toList();
    }

    private Instant at(LocalDate day, int hour) {
        return day.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant();
    }

    private record Employee(String id, String fullName) {
    }

    private record Booking(String psychologistId, Instant startsAt, Employee employee) {

        boolean overlaps(Booking other) {
            Instant endsAt = startsAt.plus(SESSION);
            Instant otherEndsAt = other.startsAt.plus(SESSION);
            boolean samePsychologist = psychologistId.equals(other.psychologistId);
            boolean sameEmployee = employee != null && other.employee != null
                    && employee.id().equals(other.employee.id());
            return (samePsychologist || sameEmployee)
                    && startsAt.isBefore(otherEndsAt) && other.startsAt.isBefore(endsAt);
        }
    }
}
