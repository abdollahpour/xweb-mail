
/**
 * XWeb project. 2012~2014.
 * Hamed Abdollahpour
 * https://github.com/abdollahpour/xweb
 */

package ir.xweb.module;

import com.sun.mail.smtp.SMTPTransport;
import ir.xweb.util.Validator;
import org.apache.commons.fileupload.FileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

/**
 * XWeb mail module.
 * <p>Send email over SMTP with java mail</p>
 * @author Hamed Abdollahpour
 */
public class MailModule extends Module {

    private static Logger logger = LoggerFactory.getLogger("MailModule");

    public static final String PARAM_FROM = "from";

    public static final String PARAM_USERNAME = "username";

    public static final String PARAM_PASSWORD = "password";

    public static final String PARAM_PORT = "port";

    public static final String PARAM_HOST = "host";

    public static final String PARAM_SSL = "ssl";

    private final String from;

    private final String username;

    private final String password;

    private final Properties props;

    private boolean deactivateActiveMails = false;

    private MailModuleData data;

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
            props.put("mail.smtp.connectiontimeout", 1000);
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

    @Override
    public void init(ServletContext context) {
        this.data = getManager().getImplemented(MailModuleData.class, null);
    }

    @Override
    public void process(
            final ServletContext context,
            final HttpServletRequest request,
            final HttpServletResponse response,
            final ModuleParam params,
            final Map<String, FileItem> files) throws IOException
    {
        // check just for schedules
        if("localhost".equalsIgnoreCase(request.getRemoteHost())) {
            if(!deactivateActiveMails) {
                deactivateActiveMails = true;
                this.data.deactivateActiveMails();
            }

            if(params.containsKey("send")) {
                Mail mail;
                while (!Thread.interrupted() && (mail = this.data.nextMail()) != null) {
                    try {
                        logger.trace("Try to send mail: " + mail);

                        this.data.changeMailStatus(mail, Mail.STATUS_ACTIVE);
                        _send(mail);
                        this.data.changeMailStatus(mail, Mail.STATUS_SENT);

                        logger.trace("Mail sent successfully: " + mail);
                    } catch (Exception ex) {
                        logger.error("Error to send email: " + mail, ex);
                        try {
                            this.data.changeMailStatus(mail, Mail.STATUS_FAILED);
                        } catch (Exception ex2) {
                            logger.error("Error to change mail status", ex2);
                        }
                    }
                }
            }

        }
    }

    /**
     * Send new email. If <code>MailModuleData</code> present, this method will work asynchronous except it works
     * synchronous.
     * @param mail Mail
     * @throws IOException
     */
    public void send(final Mail mail) throws IOException {
        // synchronous
        if(this.data == null) {
            _send(mail);
        }

        // asynchronous
        else {
            this.data.addMail(mail);
        }
    }

    public void _send(final Mail mail) throws IOException {
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

            if(mail.files() != null) {
                for(String filename:mail.files()) {
                    MimeBodyPart messageBodyPart = new MimeBodyPart();
                    messageBodyPart.setDataHandler(new DataHandler(new FileDataSource(filename)));
                    messageBodyPart.setFileName(filename);

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

    private Address[] toAddress(final List<String> to) throws AddressException {
        final Address[] addresses = new Address[to.size()];
        for(int i=0; i<to.size(); i++) {
            addresses[i] = new InternetAddress(to.get(i).trim());
        }

        return addresses;
    }

    /**
     * Check String is HTML string or not.
     * @param s Text or HTML
     * @return True if it's HTML
     */
    private boolean isHtml(final String s) {
        for(int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            if("\r\n\t ".indexOf(c) == -1) {
                return c == '<';
            }
        }
        return false;
    }

    /**
     * Validate emails.
     * @param m Mail
     */
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
            public Collection<String> files() {
                return null;
            }

            @Override
            public String from() {
                return null;
            }
        };
    }

}
