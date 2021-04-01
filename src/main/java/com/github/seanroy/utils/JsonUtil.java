package com.github.seanroy.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.seanroy.plugins.LambdaFunction;

import java.io.IOException;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;
import static com.fasterxml.jackson.annotation.PropertyAccessor.GETTER;
import static com.fasterxml.jackson.annotation.PropertyAccessor.IS_GETTER;
import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES;
import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS;

/**
 * I am serializing and deserializing classes to/from json.
 *
 * @author <a href="mailto:krzysztof@flowlab.no">Krzysztof Grodzicki</a> 06/08/16.
 */
public class JsonUtil {
    public static final ObjectMapper mapper = new ObjectMapper()
            .setVisibility(FIELD, ANY)
            .setVisibility(GETTER, NONE)
            .setVisibility(IS_GETTER, NONE)
            .enable(ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(ALLOW_SINGLE_QUOTES)
            .disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(WRITE_DATES_AS_TIMESTAMPS)
            .disable(WRITE_DATE_KEYS_AS_TIMESTAMPS)
            .setSerializationInclusion(NON_NULL);

    private JsonUtil() {
    }

    public static String toJson(Object message) throws JsonProcessingException {
        return mapper.writeValueAsString(message);
    }

    public static List<LambdaFunction> fromJson(String body) throws IOException {
        return mapper.readValue(body, new TypeReference<List<LambdaFunction>>(){});
    }
}
