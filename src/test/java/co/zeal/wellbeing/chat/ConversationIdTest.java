package co.zeal.wellbeing.chat;

import co.zeal.wellbeing.shared.error.ResourceNotFoundException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationIdTest {

    @Test
    void parsesEmployeeAndPsychologist() {
        ConversationId id = ConversationId.parse("usr-empleado-001:usr-psicologo-001");
        assertThat(id.employeeId()).isEqualTo("usr-empleado-001");
        assertThat(id.psychologistId()).isEqualTo("usr-psicologo-001");
        assertThat(id.includes("usr-psicologo-001")).isTrue();
        assertThat(id.includes("usr-empleado-002")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "a:", ":b", "a:b:c"})
    void rejectsMalformedIds(String raw) {
        assertThatThrownBy(() -> ConversationId.parse(raw)).isInstanceOf(ResourceNotFoundException.class);
    }
}
