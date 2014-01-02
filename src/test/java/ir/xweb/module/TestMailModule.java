package ir.xweb.module;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

public class TestMailModule extends TestModule {

    public TestMailModule() throws IOException {
        super();
    }

    @Test
    public void testGmail() throws Exception {
        final File dir = new File("sample");
        final TemplateEngine templateEngine = new TemplateEngine(dir);
        //final ResourceModule r = manager.getModuleOrThrow(ResourceModule.class);

        final HashMap<String, String> params = new HashMap<String, String>();
        params.put("name", "Hamed Abdollahpour");

        final File attachment = new File(dir, "simple.xsl");

        final MailModule module = manager.getModule(MailModule.class);
        module.sendEmail(
                Arrays.asList(module.getProperties().get("email")), // send it back to yourself
                Arrays.asList(module.getProperties().get("email")),
                "test",
                templateEngine.apply("simple", params),
                Arrays.asList(attachment));
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