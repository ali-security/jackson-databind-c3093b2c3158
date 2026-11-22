package com.fasterxml.jackson.databind.views;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.BaseMapTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

// [databind#6060]: `@JsonView` by-passed for `@JsonUnwrapped` Field/Setter properties
public class UnwrappedViewBypass6060Test extends BaseMapTest
{
    public static class PublicView {}
    public static class AdminView extends PublicView {}

    public static class AccountFlags {       // admin-only block
        public String role;
        public boolean approved;
        public long creditBalance;
    }
    public static class Registration {       // bound at the public registration endpoint
        @JsonView(PublicView.class) public String email;
        @JsonView(PublicView.class) public String password;
        @JsonView(AdminView.class) @JsonUnwrapped public AccountFlags flags;   // admin-only, unwrapped
    }
    public static class Control {            // identical block, NOT unwrapped
        @JsonView(PublicView.class) public String email;
        @JsonView(AdminView.class)  public AccountFlags flags;
    }

    public static class RegistrationWithSetters {   // same as Registration, but via setters
        String email;
        String password;
        AccountFlags flags;

        @JsonView(PublicView.class)
        public void setEmail(String email) { this.email = email; }

        @JsonView(PublicView.class)
        public void setPassword(String password) { this.password = password; }

        @JsonView(AdminView.class) @JsonUnwrapped
        public void setFlags(AccountFlags flags) { this.flags = flags; }
    }

    // Same as Registration, but instantiated via property-based Creator
    public static class RegistrationWithCreator {
        final String email;

        @JsonView(AdminView.class) @JsonUnwrapped public AccountFlags flags;

        @JsonCreator
        public RegistrationWithCreator(@JsonProperty("email") String email) {
            this.email = email;
        }
    }

    // Same as Registration, but deserialized via Builder
    @JsonDeserialize(builder = BuiltRegistration.Builder.class)
    public static class BuiltRegistration {
        final String email;
        final AccountFlags flags;

        BuiltRegistration(Builder b) {
            email = b.email;
            flags = b.flags;
        }

        @JsonPOJOBuilder(withPrefix = "set")
        public static class Builder {
            String email;
            AccountFlags flags;

            @JsonView(PublicView.class)
            public Builder setEmail(String email) { this.email = email; return this; }

            @JsonView(AdminView.class) @JsonUnwrapped
            public Builder setFlags(AccountFlags flags) { this.flags = flags; return this; }

            public BuiltRegistration build() { return new BuiltRegistration(this); }
        }
    }

    private final static String ATTACK_JSON = a2q("{'email':'mallory@evil.test','password':'pw',"
            + "'role':'ADMIN','approved':true,'creditBalance':1000000}");

    private final static String ADMIN_JSON = a2q("{'email':'admin@corp.test','password':'pw',"
            + "'role':'ADMIN','approved':true,'creditBalance':1000000}");

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    public void testUnwrappedBypass() throws Exception
    {
        ObjectMapper mapper = sharedMapper();

        String jsonC = a2q("{'email':'e','flags':{'role':'ADMIN','approved':true,'creditBalance':1000000}}");
        Control c = mapper.readerWithView(PublicView.class)
                .forType(Control.class)
                .readValue(jsonC);
        assertEquals("e", c.email);
        assertNull("expected control flag to be null in PublicView read", c.flags);

        String jsonR = ATTACK_JSON;
        Registration r = mapper.readerWithView(PublicView.class)
                .forType(Registration.class)
                .readValue(jsonR);
        assertEquals("mallory@evil.test", r.email);
        assertEquals("pw", r.password);
        // JsonUnwrapped flag in Registration class may affect this
        assertNull("expected registration flag to be null in PublicView read", r.flags);
    }

    // Negative control: AdminView read MUST populate the unwrapped admin-only block,
    // so the PublicView assertion above is not passing vacuously.
    public void testUnwrappedVisibleInAdminView() throws Exception
    {
        ObjectMapper mapper = sharedMapper();

        String jsonR = ADMIN_JSON;
        Registration r = mapper.readerWithView(AdminView.class)
                .forType(Registration.class)
                .readValue(jsonR);
        assertEquals("admin@corp.test", r.email);
        assertNotNull("expected registration flag to be populated in AdminView read", r.flags);
        assertEquals("ADMIN", r.flags.role);
        assertEquals(1000000, r.flags.creditBalance);
    }

    public void testUnwrappedBypassWithSetters() throws Exception
    {
        ObjectMapper mapper = sharedMapper();

        String jsonR = ATTACK_JSON;
        RegistrationWithSetters r = mapper.readerWithView(PublicView.class)
                .forType(RegistrationWithSetters.class)
                .readValue(jsonR);
        assertEquals("mallory@evil.test", r.email);
        assertEquals("pw", r.password);
        // JsonUnwrapped flag in RegistrationWithSetters class may affect this
        assertNull("expected registration flag to be null in PublicView read", r.flags);
    }

    // Negative control for the setter variant: AdminView read MUST populate flags.
    public void testUnwrappedVisibleInAdminViewWithSetters() throws Exception
    {
        ObjectMapper mapper = sharedMapper();

        String jsonR = ADMIN_JSON;
        RegistrationWithSetters r = mapper.readerWithView(AdminView.class)
                .forType(RegistrationWithSetters.class)
                .readValue(jsonR);
        assertEquals("admin@corp.test", r.email);
        assertNotNull("expected registration flag to be populated in AdminView read", r.flags);
        assertEquals("ADMIN", r.flags.role);
        assertEquals(1000000, r.flags.creditBalance);
    }

    // Same bypass, but through `readerForUpdating()` (deserialization into existing bean)
    public void testUnwrappedBypassWhenUpdating() throws Exception
    {
        ObjectMapper mapper = sharedMapper();

        Registration r = mapper.readerForUpdating(new Registration())
                .withView(PublicView.class)
                .readValue(ATTACK_JSON);
        assertEquals("mallory@evil.test", r.email);
        assertNull("expected registration flag to be null in PublicView update", r.flags);

        r = mapper.readerForUpdating(new Registration())
                .withView(AdminView.class)
                .readValue(ADMIN_JSON);
        assertNotNull("expected registration flag to be populated in AdminView update", r.flags);
        assertEquals("ADMIN", r.flags.role);
    }

    // Same bypass, but through property-based Creator
    public void testUnwrappedBypassWithCreator() throws Exception
    {
        ObjectMapper mapper = sharedMapper();

        RegistrationWithCreator r = mapper.readerWithView(PublicView.class)
                .forType(RegistrationWithCreator.class)
                .readValue(ATTACK_JSON);
        assertEquals("mallory@evil.test", r.email);
        assertNull("expected creator flag to be null in PublicView read", r.flags);

        r = mapper.readerWithView(AdminView.class)
                .forType(RegistrationWithCreator.class)
                .readValue(ADMIN_JSON);
        assertEquals("admin@corp.test", r.email);
        assertNotNull("expected creator flag to be populated in AdminView read", r.flags);
        assertEquals("ADMIN", r.flags.role);
        assertEquals(1000000, r.flags.creditBalance);
    }

    // Same bypass, but through Builder-based deserialization
    public void testUnwrappedBypassWithBuilder() throws Exception
    {
        ObjectMapper mapper = sharedMapper();

        BuiltRegistration r = mapper.readerWithView(PublicView.class)
                .forType(BuiltRegistration.class)
                .readValue(ATTACK_JSON);
        assertEquals("mallory@evil.test", r.email);
        assertNull("expected builder flag to be null in PublicView read", r.flags);

        r = mapper.readerWithView(AdminView.class)
                .forType(BuiltRegistration.class)
                .readValue(ADMIN_JSON);
        assertEquals("admin@corp.test", r.email);
        assertNotNull("expected builder flag to be populated in AdminView read", r.flags);
        assertEquals("ADMIN", r.flags.role);
        assertEquals(1000000, r.flags.creditBalance);
    }
}
