package com.fasterxml.jackson.databind.views;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.BaseMapTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

// [databind#5971]: `@JsonView` must be honored for view-restricted creator
// parameters of POJOs that also have a `@JsonUnwrapped` property (bean and
// builder variants).
public class UnwrappedCreatorViewBypass5971Test extends BaseMapTest
{
    static class PublicV { }
    static class AdminV extends PublicV { }

    static class Extra {
        public String note;
    }

    static class UnwrappedCreatorBean {
        final String name;
        final String password;

        @JsonUnwrapped
        public Extra extra;

        @JsonCreator
        public UnwrappedCreatorBean(@JsonProperty("name") String n,
                @JsonProperty("password") @JsonView(AdminV.class) String p) {
            name = n;
            password = p;
        }
    }

    @JsonDeserialize(builder = UnwrappedCreatorUser.Builder.class)
    static class UnwrappedCreatorUser {
        final String name, password;
        final Extra extra;

        UnwrappedCreatorUser(String n, String p, Extra e) {
            name = n;
            password = p;
            extra = e;
        }

        @JsonPOJOBuilder(withPrefix = "with")
        static class Builder {
            final String name, password;
            Extra extra;

            @JsonCreator
            public Builder(@JsonProperty("name") String n,
                    @JsonProperty("password") @JsonView(AdminV.class) String p) {
                name = n;
                password = p;
            }

            @JsonUnwrapped
            public Builder withExtra(Extra e) { extra = e; return this; }

            public UnwrappedCreatorUser build() {
                return new UnwrappedCreatorUser(name, password, extra);
            }
        }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    public void testUnwrappedBeanViewHonoredForCreatorProp() throws Exception {
        String json = a2q("{'password':'BYPASS','note':'n','name':'alice'}");
        UnwrappedCreatorBean b = MAPPER.readerFor(UnwrappedCreatorBean.class)
                .withView(PublicV.class).readValue(json);
        assertEquals("alice", b.name);
        assertNotNull(b.extra);
        assertEquals("n", b.extra.note);
        assertNull("password should be hidden by active view", b.password);
    }

    public void testUnwrappedBeanAdminViewStillWritesCreatorProp() throws Exception {
        String json = a2q("{'password':'OK','note':'n','name':'alice'}");
        UnwrappedCreatorBean b = MAPPER.readerFor(UnwrappedCreatorBean.class)
                .withView(AdminV.class).readValue(json);
        assertEquals("OK", b.password);
        assertEquals("n", b.extra.note);
    }

    public void testUnwrappedBuilderViewHonoredForCreatorProp() throws Exception {
        String json = a2q("{'password':'BYPASS','note':'n','name':'alice'}");
        UnwrappedCreatorUser u = MAPPER.readerFor(UnwrappedCreatorUser.class)
                .withView(PublicV.class).readValue(json);
        assertEquals("alice", u.name);
        assertNotNull(u.extra);
        assertEquals("n", u.extra.note);
        assertNull("password should be hidden by active view", u.password);
    }

    public void testUnwrappedBuilderAdminViewStillWritesCreatorProp() throws Exception {
        String json = a2q("{'password':'OK','note':'n','name':'alice'}");
        UnwrappedCreatorUser u = MAPPER.readerFor(UnwrappedCreatorUser.class)
                .withView(AdminV.class).readValue(json);
        assertEquals("OK", u.password);
        assertEquals("n", u.extra.note);
    }
}
