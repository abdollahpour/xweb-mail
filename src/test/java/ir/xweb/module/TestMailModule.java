package ir.xweb.module;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

public class TestMailModule extends TestModule {

    public TestMailModule() throws IOException {
        super();
    }

    @Test
    public void testGmail() throws Exception {
        final File dir = new File("sample");
        final TemplateEngine templateEngine = new TemplateEngine(dir);

        final HashMap<String, String> params = new HashMap<String, String>();
        params.put("name", "Hamed Abdollahpour");

        final MailModule module = manager.getModule(MailModule.class);
        module.sendEmail(
                Arrays.asList("ha.hamed@gmail.com"),
                Arrays.asList("telecom_hamed@yahoo.com"),
                "test",
                templateEngine.apply("simple", params),
                (File[])null);
    }

    @Test
    public void testTemplateEngine() throws Exception {
        final File dir = new File("sample");
        final TemplateEngine templateEngine = new TemplateEngine(dir);

        final HashMap<String, String> params = new HashMap<String, String>();
        params.put("name", "Hamed Abdollahpour");

        System.out.println(templateEngine.apply("simple", params));
    }
}