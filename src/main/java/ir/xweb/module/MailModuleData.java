package ir.xweb.module;

import java.util.Date;
import java.util.List;

/**
 * Data storage and retrieve for <code>MailModule</code>.
 */
public interface MailModuleData {

    /**
     * Add new mail object.
     * @param mail Mail
     * @return True if successful.
     */
    boolean addMail(Mail mail);

    /**
     * Change mail status.
     * @param mail
     * @param status
     * @return
     */
    boolean changeMailStatus(Mail mail, int status);

    /**
     * Get next mail to send.
     * @return Next mail
     */
    Mail nextMail();

    /**
     * List of mails.
     * @return List of mail objects.
     */
    List<Mail> mails();

    /**
     * List of mails with specific status.
     * @param status Status
     * @return List of mail objects.
     */
    List<Mail> mailsToSend(int status);

    /**
     *
     * @param before
     * @return
     */
    int clearMailsBefore(Date before);

    int deleteMail(Long id);

    int deactivateActiveMails();

}
