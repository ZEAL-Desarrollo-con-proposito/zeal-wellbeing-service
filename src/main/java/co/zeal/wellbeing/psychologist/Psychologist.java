package co.zeal.wellbeing.psychologist;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "psychologists")
public class Psychologist {
    @Id
    private String id;
    @Column(nullable = false)
    private String fullName;
    @Column(nullable = false)
    private String title;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "psychologist_focus_areas", joinColumns = @JoinColumn(name = "psychologist_id"))
    @Column(name = "focus_area")
    private List<String> focusAreas = new ArrayList<>();

    public Psychologist() {}
    public Psychologist(String id, String fullName, String title, List<String> focusAreas) {
        this.id = id; this.fullName = fullName; this.title = title; this.focusAreas = focusAreas;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<String> getFocusAreas() { return focusAreas; }
    public void setFocusAreas(List<String> focusAreas) { this.focusAreas = focusAreas; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String id, fullName, title;
        private List<String> focusAreas = new ArrayList<>();
        public Builder id(String id) { this.id = id; return this; }
        public Builder fullName(String v) { this.fullName = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder focusAreas(List<String> v) { this.focusAreas = v; return this; }
        public Psychologist build() { return new Psychologist(id, fullName, title, focusAreas); }
    }
}
