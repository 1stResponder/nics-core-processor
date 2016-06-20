/**
 * Copyright (c) 2008-2016, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.mit.ll.nics.processor.email;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.util.Properties;
import java.util.regex.Pattern;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.ByteArrayDataSource;
import javax.imageio.*;
import javax.activation.*;
import javax.xml.bind.*;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import edu.mit.ll.nics.common.email.*;
import edu.mit.ll.nics.common.email.constants.*;
import edu.mit.ll.nics.common.email.exception.*;

/**
 * Processes XML message to extract e-mail message and sends message using
 * specified mail server
 */
public class EmailConsumerSpring implements Processor {
    
    /**
     * <p>Member: LOG</p>
     * <p>Description:
     * The logger.
     * </p>
     */
    private static final Logger LOG = Logger.getLogger(EmailConsumerSpring.class);
    private Unmarshaller unmarsh = null;
    private EmailType email = null;
       
    // Properties
    
    /** The mail server hostname/IP to use */
    private String mailUrl = null;

    
    /** The log4j.properties file used by the Spring application */
    private String log4jPropertyFile;

    private String smtpHost = null;

    private String smtpPort = null;

    private String mailUsername = null;

    private String mailPassword = null;

    /**
     * Default constructor, required by Spring
     */
    public EmailConsumerSpring() {
    }
      
    /**
     * This method is called by Spring once all the properties
     * have been read as specified in the spring .xml file
     * 
     * @throws JAXBException
     */
    public void init() throws JAXBException {
    	PropertyConfigurator.configure(log4jPropertyFile);
    	
    	try { // create JAXB objects
            JAXBContext jaxbContext = JAXBContext.newInstance(XmlEmail.class.getPackage().getName());
            unmarsh = jaxbContext.createUnmarshaller(); // get an unmarshaller
        } catch (JAXBException e) {
            LOG.warn("Exception getting JAXB unmarshaller: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Unmarshall xml message into an EmailType JAXB element then send the e-mail
     * @param e
     */
    @Override    
    public void process(Exchange e) {
        // get the XML message from the exchange
        String body = e.getIn().getBody(String.class);
        LOG.debug("Processing Message: " + body);

        if (isSimpleEmailMessage(body))
        {
            handleSimpleEmailMessage(body);
        } else
        {
            handleXmlEmailMessage(body);
        }

    }

    private boolean isSimpleEmailMessage(final String body)
    {
        try
        {
            JSONObject json = new JSONObject(body);
            LOG.debug("Message is JSON");
            return true;
        } catch (JSONException je)
        {
            LOG.debug("Message not JSON");
        }

        return false;
    }

    private boolean testJsonArray(String val)
    {
        try
        {
            if (val.startsWith("[")) {
                JSONArray arr = new JSONArray(val);
            }

            return true;
        } catch (JSONException je)
        {
            return false;
        }
    }

    private String getRecipientsFromJson(String val)
    {
        StringBuffer ret = new StringBuffer();
        try
        {
            if (val.startsWith("["))
            {
                JSONArray arr = new JSONArray(val);
                LOG.debug("Emails in array: " + arr.length());
                for (int i=0; i < arr.length(); i++)
                {
                    String email = arr.getString(i);
                    LOG.debug("Extracting email from JSONArray: " + email);

                    if (ret.length() != 0)
                        ret.append(",");
                    ret.append(email);
                }
            }
        } catch (JSONException je)
        {

        }

        return ret.toString();
    }

    private String validateRecipients(String recipients){
    	if (testJsonArray(recipients))
            recipients = getRecipientsFromJson(recipients);
        Pattern pattern = Pattern.compile("^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");
    	LOG.debug("Validating receipients: " + recipients);
    	StringBuffer validated = new StringBuffer();
    	String [] addresses = recipients.split(",");
    	for(int i=0; i<addresses.length; i++){
    		String email = addresses[i].trim();
    		if(pattern.matcher(email).find()){
    			if(validated.length() != 0){
    				validated.append(",");
    			}
    			validated.append(email);
    		}else{
    			LOG.debug("Removing invalid address: " + addresses[i]);
    		}
    	}
    	return validated.toString();
    }

    private Session createSession(String from)
    {
        Properties props = new Properties();
        props.put(EmailConstants.MAIL_FROM_PROP, from);

        props.put(EmailConstants.MAIL_STARTTLS, true);
        props.put(EmailConstants.MAIL_HOST_PROP, smtpHost);
        props.put(EmailConstants.MAIL_PORT_PROP, smtpPort);
        props.put(EmailConstants.MAIL_AUTH_KEY, true);
//        props.put(EmailConstants.MAIL_USER_KEY, mailUsername);
//        props.put(EmailConstants.MAIL_PASSWD_KEY, mailPassword);


        Session session = Session.getDefaultInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(mailUsername,mailPassword);
                    }
                });


        return session;
    }

    private MimeMessage createMimeMessage(Session session)
    {
        return new MimeMessage(session);
    }

    private MimeMessage createMimeMessage(Session session, String to, String subject)
            throws MessagingException
    {
        MimeMessage msg = createMimeMessage(session);
//        msg.setFrom();

        msg.setRecipients(Message.RecipientType.TO,
                validateRecipients(to));

        msg.setSubject(subject);

        return msg;
    }

    private MimeMessage createMimeMessage(String from, String to, final String subject)
            throws MessagingException
    {
        return createMimeMessage(createSession(from), to, subject);
    }

    private MimeMessage setTextMessageBody(MimeMessage msg, final String body)
            throws MessagingException
    {
        if (body.contains("<html>"))
        {
//            msg.setText(body, "utf-8", "text/html");
            msg.setContent(body, "text/html; charset=utf-8");
        } else
        {
            msg.setText(body);
        }


        return msg;
    }

    private void sendMessage(Session session, MimeMessage msg) throws MessagingException
    {
        Transport transport = session.getTransport("smtps");
//        transport.connect(smtpHost, Integer.valueOf(smtpPort), mailUsername, mailPassword);
        transport.connect(smtpHost, mailUsername, mailPassword);
        LOG.debug("Transport: "+transport.toString());
        transport.sendMessage(msg, msg.getAllRecipients());

//        Transport.send(msg); // cause of duplicates
    }

    private void handleSimpleEmailMessage(String message)
    {
        try
        {
            JsonEmail je = JsonEmail.fromJSONString(message);
            final String to = je.getTo().trim();
            final String from = je.getFrom().trim();
            final String subject = je.getSubject().trim();
            final String body = je.getBody();

            Session session = createSession(from);
            MimeMessage msg = createMimeMessage(session, to, subject);
            msg = setTextMessageBody(msg, body);

            sendMessage(session, msg);
            LOG.debug("Message sent");
        } catch (JsonEmailException jee)
        {
            LOG.error("Caught JsonEmailException");
            jee.printStackTrace();
        } catch (MessagingException me)
        {
            LOG.error("Caught MessageException: " + me.getMessage(), me);
//            me.printStackTrace();
        }
    }

    private void handleXmlEmailMessage(String body)
    {
        // put the body into a string reader class
        java.io.StringReader sr = new java.io.StringReader(body);

        JAXBElement<EmailType> email_t;
        try {
            //Unmarshall the XML into Email object
            email_t = (JAXBElement<EmailType>) unmarsh.unmarshal(sr);
            email = email_t.getValue();

            //Build MimeMessage from email object
            Session session = createSession(email.getHeader().getFrom());
            try {
                //Add e-mail header

                MimeMessage msg = createMimeMessage(session, email.getHeader().getTo(), email.getHeader().getSubject());

                // add CC recipients
                if (email.getHeader().getCc() != null) {
                    msg.addRecipients(Message.RecipientType.CC,
                            validateRecipients(email.getHeader().getCc()));
                }

                //Create and add the e-mail body
                //If no images are included just add body text
                if (email.getContent().getImage().getLocation() == null
                        && email.getContent().getBody().getFormat() != null) {
                    String body_text = email.getContent().getBody().getText();
                    if (email.getContent().getBody().getFormat().equals("HTML")) {
                        msg.setContent(body_text, "text/html");
                    } else {
                        msg.setText(body_text);
                    }
                } else {
                    //If Images are included create a multipart email body
                    Multipart multipartbody;
                    //Buffer the image
                    BufferedImage img = (BufferedImage) email.getContent().getImage().getJPEGPicture();
                    //Create a message body part and add the image
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(img, "jpeg", bos);
                    MimeBodyPart imageBodyPart = new MimeBodyPart();
                    imageBodyPart.setDataHandler(new DataHandler(new ByteArrayDataSource(bos.toByteArray(), "image/jpeg")));
                    //Create the multipart body and add the image bodypart
                    if (email.getContent().getImage().getLocation().equals("embed")) {
                        //Embed image
                        multipartbody = new MimeMultipart("related");
                        imageBodyPart.setHeader("Content-ID", "<embedded_image>");
                    } else {
                        //Attach image
                        multipartbody = new MimeMultipart();
                        imageBodyPart.setFileName("image.jpg");
                    }

                    //add the text bodypart
                    if (email.getContent().getBody().getFormat() != null) {
                        MimeBodyPart messageBodyPart = new MimeBodyPart();
                        String body_text = email.getContent().getBody().getText();
                        if (email.getContent().getImage().getLocation().equals("embed")
                                && email.getContent().getBody().getFormat().equals("HTML")) {
                            //Embedded image in html message
                            //Insert image at end of body tag
                            messageBodyPart.setContent(body_text.substring(0, body_text.lastIndexOf("</body>"))
                                    + ("<br/><br/><img src=\"cid:embedded_image\">")
                                    + (body_text.substring(body_text.lastIndexOf("</body>"))), "text/html");
                        } else if (email.getContent().getImage().getLocation().equals("embed")) {
                            //Embedded image in regular text body
                            //Convert into html message
                            messageBodyPart.setContent("<html><body>"
                                    + body_text + "<br/><br/><img src=\"cid:embedded_image\">"
                                    + "</body></html>", "text/html");
                        } else if (email.getContent().getBody().getFormat().equals("HTML")) {
                            //Attached image with html body
                            messageBodyPart.setContent(body_text, "text/html");
                        } else {
                            //Attached image with text body
                            messageBodyPart.setText(body_text);
                        }
                        multipartbody.addBodyPart(messageBodyPart);
                        multipartbody.addBodyPart(imageBodyPart);
                    }
                    msg.setContent(multipartbody);
                }

                //Send the message
                Transport.send(msg);
                LOG.info("Message sent to:" + email.getHeader().getTo());
            } catch (MessagingException mex) {
                System.out.println("send failed, exception: " + mex);
            }
        } catch (Exception ex) {
            LOG.error("EmailSender:process: caught following "
                    + "exception processing XML: "
                    + ex.getMessage(),ex);
        }
    }

    
    // Getters and Setters
    
	public String getMailUrl() {
		return mailUrl;
	}

	public void setMailUrl(String mailUrl) {
		this.mailUrl = mailUrl;
	}


	public String getLog4jPropertyFile() {
		return log4jPropertyFile;
	}

	public void setLog4jPropertyFile(String log4jPropertyFile) {
		this.log4jPropertyFile = log4jPropertyFile;
	}

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public String getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(String smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getMailUsername() {
        return mailUsername;
    }

    public void setMailUsername(String mailUsername) {
        this.mailUsername = mailUsername;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }
}
