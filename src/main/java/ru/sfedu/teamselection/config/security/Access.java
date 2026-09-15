package ru.sfedu.teamselection.config.security;

/**
 * {@code @PreAuthorize} expressions for the access matrix (vaimon/team-selection#7). Roles are current per request,
 * see {@link CurrentAuthoritiesFilter}.
 */
public final class Access {
    public static final String ADMIN = "hasRole('ADMIN')";

    /** Filled the questionnaire for the current selection, or admin: profiles, contacts, teams, applications. */
    public static final String PARTICIPANT_OR_ADMIN = "hasAnyRole('ADMIN', 'PARTICIPANT')";

    private Access() {
    }
}
