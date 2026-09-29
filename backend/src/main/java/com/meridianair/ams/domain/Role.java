package com.meridianair.ams.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    /** Reserved for fine-grained checks beyond role name - not enforced
     * anywhere yet, same state as the Enterprise Model-2 reference this
     * was ported from. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission")
    private Set<String> permissions = new HashSet<>();

    private boolean systemRole;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Role() {
    }

    public Role(String name, String description, boolean systemRole) {
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Set<String> getPermissions() { return permissions; }
    public boolean isSystemRole() { return systemRole; }
    public Instant getCreatedAt() { return createdAt; }
}
