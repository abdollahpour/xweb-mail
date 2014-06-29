
/**
 * XWeb
 * Hamed Abdollahpour
 * https://github.com/abdollahpour/xweb
 */

package ir.xweb.module;

import java.util.Collection;
import java.util.List;

/**
 * XWeb mail data.
 */
public interface Mail {

    /**
     * Wait for sending.
     */
    int STATUS_IDLE = 0;

    /**
     * Sending now.
     */
    int STATUS_ACTIVE = 1;

    /**
     * Field to send
     */
    int STATUS_FAILED = 2;

    /**
     * Sent.
     */
    int STATUS_SENT = 3;

    /**
     * Destination addresses as TO
     * @return List of emails or Null if you don't need it.
     */
    List<String> to();

    /**
     * Destination addresses as BCC
     * @return List of emails or Null if you don't need it.
     */
    List<String> bcc();

    /**
     * Destination addresses as CC
     * @return List of emails or Null if you don't need it.
     */
    List<String> cc();

    /**
     * List of replay recipients.
     * @return
     */
    List<String> replayTo();

    /**
     * Email title
     * @return title
     */
    String subject();

    /**
     * Email body. If String starts with &gt; character, will send as HTML email.
     * @return HTML or simple text string
     */
    String body();

    /**
     * List of file attachments.
     * @return
     */
    Collection<String> files();

    /**
     * From sender email
     * @return
     */
    String from();

}
