package com.fasterxml.jackson.databind.views;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

// GHSA-mhm7-754m-9p8w / [databind#5971]: `@JsonView` must be honored for plain
// (not type-id related) view-restricted creator parameters of a bean that uses
// an EXTERNAL_PROPERTY type id (the property-based-creator + external-type-id
// branch of BeanDeserializer).
public class CreatorViewBypassExtTypeIdTest
{
    static String a2q(String j) { return j.replace('\'', '"'); }

    static class PublicV { }
    static class AdminV extends PublicV { }

    static abstract class Asset {
        public String name;
    }

    static class PublicAsset extends Asset { }

    // Bean with an EXTERNAL_PROPERTY type id and a plain (not type-id related)
    // view-restricted CREATOR parameter
    static class ExtTypeCreatorBean {
        final String label;
        final String password;

        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
                include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
                property = "kind")
        @JsonSubTypes({
                @JsonSubTypes.Type(value = PublicAsset.class, name = "pub")
        })
        public Asset asset;

        @JsonCreator
        public ExtTypeCreatorBean(@JsonProperty("label") String l,
                @JsonProperty("password") @JsonView(AdminV.class) String p,
                @JsonProperty("asset") Asset a) {
            label = l;
            password = p;
            asset = a;
        }
    }

    private final ObjectMapper MAPPER = new ObjectMapper();

    /*
    /**********************************************************************
    /* Bean, external type id
    /**********************************************************************
     */

    @Test
    public void extTypeIdBeanViewHonoredForCreatorProp() throws Exception {
        String json = a2q("{'password':'BYPASS','label':'hello','kind':'pub','asset':{'name':'foo'}}");
        ExtTypeCreatorBean b = MAPPER.readerFor(ExtTypeCreatorBean.class)
                .withView(PublicV.class).readValue(json);
        assertEquals("hello", b.label);
        assertInstanceOf(PublicAsset.class, b.asset);
        assertEquals("foo", b.asset.name);
        assertNull(b.password, "password should be hidden by active view");
    }

    @Test
    public void extTypeIdBeanAdminViewStillWritesCreatorProp() throws Exception {
        String json = a2q("{'password':'OK','label':'hello','kind':'pub','asset':{'name':'foo'}}");
        ExtTypeCreatorBean b = MAPPER.readerFor(ExtTypeCreatorBean.class)
                .withView(AdminV.class).readValue(json);
        assertEquals("OK", b.password, "password should be writable under AdminV");
        assertInstanceOf(PublicAsset.class, b.asset);
    }
}
