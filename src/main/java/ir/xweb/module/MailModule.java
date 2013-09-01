package ir.xweb.module;

import ir.xweb.server.XWebUser;
import org.apache.commons.fileupload.FileItem;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;

public class MailModule extends Module {

    private String username;

    private String password;

    private String manager;

    public MailModule(Manager m, ModuleInfo info, ModuleParam properties) {
        super(m, info, properties);

        username = properties.getString("googlemail.username", null);
        password = properties.getString("googlemail.password", null);
        manager = properties.getString("googlemail.manager", null);
    }

    @Override
    public void process(ServletContext context,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        ModuleParam params,
                        HashMap<String, FileItem> files) throws IOException {


        String action = params.get("action", null);
        String body = params.get("body", null);
        if(body == null) {
            throw new ModuleException("body not found");
        }

        XWebUser xuser = (XWebUser)request.getSession().getAttribute(ir.xweb.server.Constants.SESSION_USER);

        if("survey".equals(action)) {
            StringBuilder t = new StringBuilder();
            t.append("mobliebrian - survey - ").append(xuser.getIdentifier());

            StringBuilder b = new StringBuilder();
            b.append("from: ").append(xuser.toString()).append('\n');
            //b.append("replay: ").append(user.email).append("\n\n");

            b.append(body);

            try {
                GoogleMail.Send(username, password, manager, t.toString(), b.toString());
            } catch (Exception e) {
                throw new ModuleException("Error sending email", e);
            }
        } else {
            String title = params.validate("title", null, true).get(null);

            StringBuilder t = new StringBuilder();
            t.append("iranwiki[bug] - ").append(title);

            StringBuilder b = new StringBuilder();
            b.append("from: ").append(xuser.toString()).append('\n');
            //b.append("replay: ").append(user.email).append("\n\n");

            b.append(body);

            try {
                GoogleMail.Send(username, password, manager, t.toString(), b.toString());
            } catch (Exception e) {
                throw new ModuleException("Error sending email", e);
            }
        }
    }


}
