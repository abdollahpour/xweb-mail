package ir.xweb.module.test;

import ir.xweb.module.MailModule;
import ir.xweb.module.TestModule;
import org.junit.Test;

import java.io.IOException;

public class TestMailModule extends TestModule {


    public TestMailModule() throws IOException {
    }

    @Test
    public void test() throws IOException {
        getManager().getModuleOrThrow(MailModule.class).send(
                MailModule.newMail("sample@gmail.com", "test", "message"));
    }

}