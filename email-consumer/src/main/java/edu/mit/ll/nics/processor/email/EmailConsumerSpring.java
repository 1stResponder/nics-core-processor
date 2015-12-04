/**
 * Copyright (c) 2008-2015, Massachusetts Institute of Technology (MIT)
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

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.*;
import javax.mail.util.ByteArrayDataSource;
import javax.imageio.*;
import javax.activation.*;
import javax.xml.bind.*;

import edu.mit.ll.nics.common.email.*;

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
        LOG.info("Processing Message");

        // put the body into a string reader class
        java.io.StringReader sr = new java.io.StringReader(body);

        JAXBElement<EmailType> email_t;
        try {
            //Unmarshall the XML into Email object
            email_t = (JAXBElement<EmailType>) unmarsh.unmarshal(sr);
            email = email_t.getValue();

            //Build MimeMessage from email object
            Properties props = new Properties();
            props.put("mail.smtp.host", mailUrl);
            props.put("mail.from", email.getHeader().getFrom());
            Session session = Session.getInstance(props, null);
            try {
                //Add e-mail header
                MimeMessage msg = new MimeMessage(session);
                msg.setFrom();
                msg.setRecipients(Message.RecipientType.TO,
                        //email.getHeader().getTo());
                		validateRecipients(email.getHeader().getTo()));
                if (email.getHeader().getCc() != null) {
                    //msg.addRecipients(Message.RecipientType.CC, email.getHeader().getCc());
                	msg.addRecipients(Message.RecipientType.CC, 
                			validateRecipients(email.getHeader().getCc()));
                }
                msg.setSubject(email.getHeader().getSubject());

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
                        //Attach imabe
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

    private String validateRecipients(String recipients){
    	Pattern pattern = Pattern.compile("^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");
    	System.out.println("Validating receipients: " + recipients);
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
    			//System.out.println("Removing invalid address: " + addresses[i]);
    			LOG.info("Removing invalid address: " + addresses[i]);
    		}
    	}
    	return validated.toString();
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
}
