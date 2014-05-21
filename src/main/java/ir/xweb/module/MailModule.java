package ir.xweb.module;

import com.sun.mail.smtp.SMTPTransport;
import ir.xweb.util.Validator;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

public class MailModule extends Module {

    public final static String PARAM_FROM = "from";

    public final static String PARAM_USERNAME = "username";

    public final static String PARAM_PASSWORD = "password";

    public final static String PARAM_PORT = "port";

    public final static String PARAM_HOST = "host";

    public final static String PARAM_SSL = "ssl";

    private final String from;

    private final String username;

    private final String password;

    private final Properties props;

    private LinkedBlockingDeque<Mail> mails = new LinkedBlockingDeque<Mail>();

    private Thread executor;

    public MailModule(final Manager m, final ModuleInfo info, final ModuleParam properties) throws ModuleException {
        super(m, info, properties);

        from = properties.validate(PARAM_FROM, Validator.VALIDATOR_EMAIL, false).getString();
        username = properties.getString(PARAM_USERNAME, from);
        password = properties.getString(PARAM_PASSWORD);

        props = System.getProperties();

        boolean ssl = properties.getBoolean(PARAM_SSL, false);
        String defaultHost = null;
        int defaultPort = 25;

        if(username != null && username.toLowerCase().endsWith("@gmail.com")) {
            defaultHost = "smtp.gmail.com";
            defaultPort = 587;

            ssl = true;
            props.setProperty("mail.smtp.socketFactory.fallback", "false");
            props.setProperty("mail.smtp.socketFactory.port", "465");
            props.setProperty("mail.smtps.auth", "true");
            props.put("mail.smtps.quitwait", "false");
            props.put("mail.smtp.starttls.enable", true);
            props.put("mail.smtp.timeout", 10000);
        } else {
            try {
                defaultHost = InetAddress.getLocalHost().getHostName();
            } catch (Exception ex) {}
        }

        if(ssl) {
            final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
            props.setProperty("mail.smtp.socketFactory.class", SSL_FACTORY);
        }

        final String host = properties.getString(PARAM_HOST, defaultHost);
        final int port = properties.getInt(PARAM_PORT, defaultPort);

        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        if(this.password != null) {
            props.put("mail.smtp.auth", true);
            props.setProperty("mail.user", username);
            props.setProperty("mail.password", password);
        }
    }

    @Deprecated
    public void sendEmail(
            final List<String> to,
            final List<String> replayTo,
            final String subject,
            final String body) throws IOException {
        sendEmail(to, replayTo, subject, body, (Map<String, DataSource>)null);
    }

    @Deprecated
    public void sendEmail(
            final List<String> to,
            final List<String> cc,
            final List<String> bcc,
            final List<String> replayTo,
            final String subject,
            final String body,
            final List<File> attachments) throws IOException {

        Map<String, DataSource> dataSources = null;
        if(attachments != null && attachments.size() > 0) {
            dataSources = new HashMap<String, DataSource>(attachments.size());
            for(File f:attachments) {
                dataSources.put(f.getName(), new FileDataSource(f));
            }
        }

        sendEmail(to, cc, bcc, replayTo, subject, body, dataSources);
    }

    @Deprecated
    public void sendEmail(
            final List<String> to,
            final List<String> replayTo,
            final String subject,
            final String body,
            final List<File> attachments) throws IOException {
        sendEmail(to, null, null, replayTo, subject, body, attachments);
    }

    @Deprecated
    public void sendEmail(
            final List<String> to,
            final List<String> replayTo,
            final String subject,
            final String body,
            final Map<String, DataSource> attachments) throws IOException {
        sendEmail(to, null, null, replayTo, subject, body, attachments);
    }

    /**
     * Send Mail asynchronously. It does not support attachments but you can use files
     * @param mail
     * @throws IOException
     */
    public synchronized void aSend(final Mail... mail) throws IOException {
        // Validate
        for(Mail m:mail) {
            validate(m);
        }

        for(Mail m:mail) {
            if(addToQ(m)) {
                if(executor == null || !executor.isAlive()) {
                    executor = new Thread() {
                        @Override
                        public void run() {
                            Mail mail;
                            while((mail = nextFromQ()) != null) {
                                try {
                                    send(mail);
                                } catch (Throwable t) {
                                    errorInQ(mail);
                                }
                            }
                        }
                    };
                    executor.start();
                }
            } else {
                throw new IllegalArgumentException("Error to add mail: " + mail);
            }
        }
    }

    /**
     * Add email to the sending Q.
     * You can Override this method to manage them by yourself (in database for example)
     * @param mail
     */
    protected synchronized boolean addToQ(final Mail mail) {
        return mails.add(mail);
    }

    protected synchronized Mail nextFromQ() {
        return mails.size() > 0 ? mails.pop() : null;
    }

    /**
     * Error happens to send email from Q.
     * You can Override this method to manage them by yourself (in database for example)
     * @param mail
     */
    protected synchronized boolean errorInQ(final Mail mail) {
        return mails.remove(mail);
    }

    public void send(final Mail mail) throws IOException {
        validate(mail);

        try {
            Session session;
            if(password != null) {
                session = Session.getDefaultInstance(props,
                        new javax.mail.Authenticator() {
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(username, password);
                            }
                        });
            } else {
                session = Session.getDefaultInstance(props);
            }

            final MimeMessage message = new MimeMessage(session);

            if(mail.replayTo() != null && mail.replayTo().size() > 0) {
                final Address[] addresses = new Address[mail.replayTo().size()];
                for(int i=0; i<mail.replayTo().size(); i++) {
                    addresses[i] = new InternetAddress(mail.replayTo().get(i));
                }
                message.setReplyTo(addresses);
            }

            if(mail.to() != null) {
                message.addRecipients(Message.RecipientType.TO, toAddress(mail.to()));
            }
            if(mail.cc() != null) {
                message.addRecipients(Message.RecipientType.CC, toAddress(mail.cc()));
            }
            if(mail.bcc() != null) {
                message.addRecipients(Message.RecipientType.BCC, toAddress(mail.bcc()));
            }
            if(mail.from() != null) {
                message.setFrom(new InternetAddress(mail.from()));
            } else if(this.from != null) {
                message.setFrom(new InternetAddress(this.from));
            } else if(username != null && username.indexOf('@') > 0) {
                message.setFrom(new InternetAddress(username));
            }
            message.setSubject(mail.subject());


            final Multipart multipart = new MimeMultipart();

            if(mail.attachments() != null) {
                for(Map.Entry<String,DataSource> e:mail.attachments().entrySet()) {
                    MimeBodyPart messageBodyPart = new MimeBodyPart();
                    messageBodyPart.setDataHandler(new DataHandler(e.getValue()));
                    messageBodyPart.setFileName(e.getKey());

                    multipart.addBodyPart(messageBodyPart);
                }
            }

            if(mail.files() != null) {
                for(File f:mail.files()) {
                    MimeBodyPart messageBodyPart = new MimeBodyPart();
                    messageBodyPart.setDataHandler(new DataHandler(new FileDataSource(f)));
                    messageBodyPart.setFileName(f.getName());

                    multipart.addBodyPart(messageBodyPart);
                }
            }


            if(isHtml(mail.body())) {
                final MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(mail.body(), "text/html; charset=utf-8");
                multipart.addBodyPart(htmlPart);

                //MimeBodyPart textPart = new MimeBodyPart();
                //textPart.setText(body.replaceAll("\\<.*?\\>", ""), "utf-8");
                //multipart.addBodyPart(textPart);
            } else {
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(mail.body().replaceAll("\\<.*?\\>", ""), "utf-8");
                multipart.addBodyPart(textPart);
            }

            // TODO: HTML email appear 2 time in GMAIL! We disable text mode now

            message.setSentDate(new Date());
            message.setContent(multipart);

            // For some reason, Transport does not work for google
            if(props.getProperty("mail.smtp.host").equals("smtp.gmail.com")) {
                final SMTPTransport t = (SMTPTransport) session.getTransport("smtps");

                t.connect("smtp.gmail.com", username, password);
                t.sendMessage(message, message.getAllRecipients());
                t.close();
            } else {
                Transport.send(message);
            }
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    @Deprecated
    public void sendEmail(
            final List<String> to,
            final List<String> cc,
            final List<String> bcc,
            final List<String> replayTo,
            final String subject,
            final String body,
            final Map<String, DataSource> attachments) throws IOException {

        send(to, cc, bcc, replayTo, subject, body, attachments);
    }

    public void send(
            final List<String> to,
            final List<String> cc,
            final List<String> bcc,
            final List<String> replayTo,
            final String subject,
            final String body,
            final Map<String, DataSource> attachments) throws IOException {

        send(new Mail() {
            @Override
            public List<String> to() {
                return to;
            }

            @Override
            public List<String> bcc() {
                return bcc;
            }

            @Override
            public List<String> cc() {
                return cc;
            }

            @Override
            public List<String> replayTo() {
                return replayTo;
            }

            @Override
            public String subject() {
                return subject;
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public List<File> files() {
                return files();
            }

            @Override
            public Map<String, DataSource> attachments() {
                return attachments;
            }

            @Override
            public String from() {
                return null;
            }
        });
    }

    private Address[] toAddress(final List<String> to) throws AddressException {
        final Address[] addresses = new Address[to.size()];
        for(int i=0; i<to.size(); i++) {
            addresses[i] = new InternetAddress(to.get(i).trim());
        }

        return addresses;
    }

    private boolean isHtml(final String s) {
        for(int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            if("\r\n\t ".indexOf(c) == -1) {
                return c == '<';
            }
        }
        return false;
    }

    private void validate(final Mail m) {
        if(m == null) {
            throw new IllegalArgumentException("null");
        }

        if((m.to() == null || m.to().size() == 0) && (m.bcc() == null || m.bcc().size() == 0) && (m.cc() == null || m.cc().size() == 0)) {
            throw new IllegalArgumentException("There should be at least one TO, BCC or CC");
        }

        if(m.subject() == null) {
            throw new IllegalArgumentException("null subject");
        }

        if(m.body() == null) {
            throw new IllegalArgumentException("null body");
        }
    }

    public static Mail newMail(final String to, final String subject, final String body) {
        return newMail(Arrays.asList(to), subject, body);
    }

    public static Mail newMail(final List<String> to, final String subject, final String body) {
        return new Mail() {
            @Override
            public List<String> to() {
                return to;
            }

            @Override
            public List<String> bcc() {
                return null;
            }

            @Override
            public List<String> cc() {
                return null;
            }

            @Override
            public List<String> replayTo() {
                return null;
            }

            @Override
            public String subject() {
                return subject;
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public List<File> files() {
                return null;
            }

            @Override
            public Map<String, DataSource> attachments() {
                return null;
            }

            @Override
            public String from() {
                return null;
            }
        };
    }

    public static interface Mail {

        public List<String> to();

        public List<String> bcc();

        public List<String> cc();

        public List<String> replayTo();

        public String subject();

        public String body();

        public List<File> files();

        public Map<String, DataSource> attachments();

        public String from();

    }

}
