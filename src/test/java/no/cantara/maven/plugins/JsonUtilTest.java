package no.cantara.maven.plugins;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:kgrodzicki@gmail.com">Krzysztof Grodzicki</a> 11/08/16.
 */
public class JsonUtilTest {

    @Test
    public void testFromJson() throws IOException {
        List<LambdaFunction> lambdaFunctions = JsonUtil.fromJson("[ "
                + "{ \"functionName\": \"LambdaFunction1\", \"description\": \"Test desc\", \"handler\": \"com.kgrodzicki.LambdaFunction1\", \"memorySize\": \"\", \"timeout\": \"\" },"
                + "{ \"functionName\": \"embriq-qf-workorder-to-soria\", \"description\": \"Test desc\", \"handler\": \"com.kgrodzicki.LambdaFunction2\", \"memorySize\": \"\", \"timeout\": \"\"} "
                + "]");
        assertTrue(lambdaFunctions.size() == 2);
    }

    @Test
    public void testGenerateConfiguration() throws IOException {
        LambdaFunction lambdaFunction0 = build("LambdaFunction0", "Test description 0", "com.kgrodzicki.Lambda0");
        LambdaFunction lambdaFunction1 = build("LambdaFunction1", "Test description 1", "com.kgrodzicki.Lambda1");

        List<LambdaFunction> lambdaFunctions = Arrays.asList(lambdaFunction0, lambdaFunction1);
        String json = JsonUtil.toJson(lambdaFunctions);

        assertTrue(json.contains("[{\"functionName\":\"LambdaFunction0\",\"description\":\"Test description 0\",\"handler\":\"com.kgrodzicki.Lambda0\"},{\"functionName\":\"LambdaFunction1\","
                + "\"description\":\"Test description 1\",\"handler\":\"com.kgrodzicki.Lambda1\"}]"));
        assertTrue(JsonUtil.fromJson(json) != null);
    }

    private LambdaFunction build(String functionName, String description, String handler) {
        return new LambdaFunction()
                .withFunctionName(functionName)
                .withDescription(description)
                .withHandler(handler);
    }
}
