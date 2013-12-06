#xweb-mail

You can send email in any part of the other modules in your application. You can't send email directly with POST and GET request right now. It support all SMTP services and also Gmail

## How can we use it?
Add module:

```xml
<module>
    <name>mail</name>
    <class>ir.xweb.module.MailModule</class>
    <properties>
        <property key='email'>your@email.com</property>
        <property key='password'>your_password</property>
    </properties>
</module>
```

Use send function to send email:
```java
final MailModule module = manager.getModule(MailModule.class);
module.sendEmail(
        Arrays.asList("ha.hamed@gmail.com"),
        Arrays.asList("telecom_hamed@yahoo.com"),
        "test",
        "some text in here",
        (File[])null);
```

## Make it
mvn clean install -Dmaven.test.skip=true