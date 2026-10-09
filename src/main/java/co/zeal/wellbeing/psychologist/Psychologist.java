package co.zeal.wellbeing.psychologist;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.List;

/** Perfil público de un psicólogo en el directorio (HU-04). Su id coincide con su cuenta de usuario. */
@Entity
@Table(name = "psychologists")
public class Psychologist {

    @Id
    private String id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String title;

    @ElementCollection
    @CollectionTable(name = "psychologist_focus_areas", joinColumns = @JoinColumn(name = "psychologist_id"))
    @OrderColumn(name = "position")
    @Column(name = "focus_area", nullable = false)
    private List<String> focusAreas = new ArrayList<>();

    @Version
    private long version;

    protected Psychologist() {
        // JPA
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getFocusAreas() {
        return List.copyOf(focusAreas);
    }
}
