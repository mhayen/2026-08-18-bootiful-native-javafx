package nl.markhayen.desktop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;

@Component
public class HtmlGenerator {
    Resource template;
    String deploymentId;

    public HtmlGenerator(@Value("classpath:include-in-cms.html") Resource template,
                         @Value("${implementation-id}") String deploymentId) {
        this.template = template;
        this.deploymentId = deploymentId;
    }
    public  String generateHtml(String formulierNaam) throws IOException {
        String html = template.getContentAsString(Charset.defaultCharset());
        return html.
                replace("<<<DEPLOYMENT_ID>>>", deploymentId).
                replace("<<<FORMULIER_NAAM>>>", formulierNaam);
    }
}
