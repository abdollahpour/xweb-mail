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

public class MailModule extends Module {

    public final static String PARAM_EMAIL = "email";

    public final static String PARAM_PASSWORD = "password";

    public final static String PARAM_HOST = "host";

    public final static String PARAM_PORT = "port";

    public final static String PARAM_DATABASE_FILE = "file.database";

    private final String email;

    private final String password;

    private final Properties props;

    private File databaseFile;

    public MailModule(final Manager m, final ModuleInfo info, final ModuleParam properties) throws ModuleException {
        super(m, info, properties);

        email = properties.validate(PARAM_EMAIL, null, true).getString(null);
        password = properties.getString(PARAM_PASSWORD, null);
        databaseFile = properties.getFile(PARAM_DATABASE_FILE, (File) null);

        if(email == null) {
            throw new IllegalArgumentException("Please email");
        }

        props = System.getProperties();

        boolean ssl = properties.getBoolean("ssl", false);;
        String defaultHost = null;
        int defaultPort = 25;

        if(email.toLowerCase().endsWith("@gmail.com")) {
            defaultHost = "smtp.gmail.com";
            defaultPort = 587;

            ssl = true;
            props.setProperty("mail.smtp.socketFactory.fallback", "false");
            props.setProperty("mail.smtp.socketFactory.port", "465");
            props.setProperty("mail.smtps.auth", "true");
            props.put("mail.smtps.quitwait", "false");
            props.put("mail.smtp.starttls.enable", true);
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
            props.setProperty("mail.user", "pdroid");
            props.setProperty("mail.password", password);
        }
    }

    public void sendEmail(
            final List<String> to,
            final List<String> replayTo,
            final String subject,
            final String body) throws IOException {
        sendEmail(to, replayTo, subject, body, (Map<String, DataSource>)null);
    }

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

    public void sendEmail(
            final List<String> to,
            final List<String> replayTo,
            final String subject,
            final String body,
            final List<File> attachments) throws IOException {
        sendEmail(to, null, null, replayTo, subject, body, attachments);
    }

    public void sendEmail(
            final List<String> to,
            final List<String> replayTo,
            final String subject,
            final String body,
            final Map<String, DataSource> attachments) throws IOException {
        sendEmail(to, null, null, replayTo, subject, body, attachments);
    }

    public void sendEmail(
            final List<String> to,
            final List<String> cc,
            final List<String> bcc,
            final List<String> replayTo,
            final String subject,
            final String body,
            final Map<String, DataSource> attachments) throws IOException {

        if(subject == null) {
            throw new IllegalArgumentException("null subject");
        }
        if(body == null) {
            throw new IllegalArgumentException("null body");
        }

        try {
            if((to == null || to.size() == 0) && (cc == null || cc.size() == 0) && (bcc == null || bcc.size() == 0)) {
                throw new IllegalArgumentException("null or empty to,cc,bcc");
            }
            if(subject == null || subject.length() == 0) {
                throw new IllegalArgumentException("null or empty subject");
            }
            if(body == null || body.length() == 0) {
                throw new IllegalArgumentException("null or empty body");
            }

            Session session;
            if(password != null) {
                session = Session.getDefaultInstance(props,
                        new javax.mail.Authenticator() {
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(email, password);
                            }
                        });
            } else {
                session = Session.getDefaultInstance(props);
            }

            final MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(email));

            if(replayTo != null && replayTo.size() > 0) {
                final Address[] addresses = new Address[replayTo.size()];
                for(int i=0; i<replayTo.size(); i++) {
                    addresses[i] = new InternetAddress(replayTo.get(i));
                }
                message.setReplyTo(addresses);
            }

            if(to != null) {
                message.addRecipients(Message.RecipientType.TO, toAddress(to));
            }
            if(cc != null) {
                message.addRecipients(Message.RecipientType.CC, toAddress(cc));
            }
            if(bcc != null) {
                message.addRecipients(Message.RecipientType.BCC, toAddress(bcc));
            }
            message.setSubject(subject);


            final Multipart multipart = new MimeMultipart();


            if(attachments != null) {
                for(Map.Entry<String,DataSource> e:attachments.entrySet()) {
                    MimeBodyPart messageBodyPart = new MimeBodyPart();
                    messageBodyPart.setDataHandler(new DataHandler(e.getValue()));
                    messageBodyPart.setFileName(e.getKey());

                    multipart.addBodyPart(messageBodyPart);
                }
            }

            if(isHtml(body)) {
                final MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(body, "text/html; charset=utf-8");
                multipart.addBodyPart(htmlPart);

                //MimeBodyPart textPart = new MimeBodyPart();
                //textPart.setText(body.replaceAll("\\<.*?\\>", ""), "utf-8");
                //multipart.addBodyPart(textPart);
            } else {
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(body.replaceAll("\\<.*?\\>", ""), "utf-8");
                multipart.addBodyPart(textPart);
            }

            // TODO: HTML email appear 2 time in GMAIL! We disable text mode now

            message.setSentDate(new Date());
            message.setContent(multipart);

            // For some reason, Transport does not work for google
            if(props.getProperty("mail.smtp.host").equals("smtp.gmail.com")) {
                final SMTPTransport t = (SMTPTransport) session.getTransport("smtps");

                t.connect("smtp.gmail.com", email, password);
                t.sendMessage(message, message.getAllRecipients());
                t.close();
            } else {
                Transport.send(message);
            }
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    private Address[] toAddress(final List<String> to) throws AddressException {
        final Address[] addresses = new Address[to.size()];
        for(int i=0; i<to.size(); i++) {
            addresses[i] = new InternetAddress(to.get(i));
        }

        return addresses;
    }

    public boolean isHtml(final String s) {
        for(int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            if("\r\n\t ".indexOf(c) == -1) {
                return c == '<';
            }
        }
        return false;
    }

}
