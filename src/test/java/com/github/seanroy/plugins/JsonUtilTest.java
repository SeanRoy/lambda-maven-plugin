package com.github.seanroy.plugins;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:krzysztof@flowlab.no">Krzysztof Grodzicki</a> 11/08/16.
 */
public class JsonUtilTest {

    @Test
    public void testFromJson() throws IOException {
        String json = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("lambda-configuration-test.json"));

        List<LambdaFunction> lambdaFunctions = JsonUtil.fromJson(json);

        assertTrue(lambdaFunctions.size() == 2);
    }

    @Test
    public void testGenerateConfiguration() throws IOException {
        LambdaFunction lambdaFunction0 = build("LambdaFunction0", "Test description 0", "com.kgrodzicki.Lambda0", environmentVariablesMock());
        LambdaFunction lambdaFunction1 = build("LambdaFunction1", "Test description 1", "com.kgrodzicki.Lambda1", environmentVariablesMock());

        List<LambdaFunction> lambdaFunctions = Arrays.asList(lambdaFunction0, lambdaFunction1);
        String json = JsonUtil.toJson(lambdaFunctions);

        assertTrue(json.contains("[{\"functionName\":\"LambdaFunction0\",\"description\":\"Test description 0\",\"handler\":\"com.kgrodzicki.Lambda0\",\"environmentVariables\""
                + ":{\"key1\":\"value1\",\"key0\":\"value0\"}},{\"functionName\":\"LambdaFunction1\",\"description\":\"Test description 1\","
                + "\"handler\":\"com.kgrodzicki.Lambda1\",\"environmentVariables\":{\"key1\":\"value1\",\"key0\":\"value0\"}}]"));
        assertTrue(JsonUtil.fromJson(json) != null);
    }

    private LambdaFunction build(String functionName, String description, String handler, Map<String, String> environmentVariables) {
        return new LambdaFunction()
                .withFunctionName(functionName)
                .withDescription(description)
                .withHandler(handler)
                .withEnvironmentVariables(environmentVariables);
    }

    private Map<String, String> environmentVariablesMock() {
        HashMap<String, String> result = new HashMap<>();
        result.put("key0", "value0");
        result.put("key1", "value1");
        return result;
    }

}
