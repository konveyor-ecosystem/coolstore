package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import jakarta.inject.Inject;
import jakarta.jms.*;
import jakarta.naming.Context;
import jakarta.naming.InitialContext;
import jakarta.naming.NamingException;
import jakarta.rmi.PortableRemoteObject;
import java.util.Hashtable;
import java.util.logging.Logger;

public class InventoryNotificationMDB implements MessageListener {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Inject
    private Logger logger;

    private final static String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";
    private final static String JMS_FACTORY = "TCF";
    private final static String TOPIC = "topic/orders";
    private TopicConnection tcon;
    private TopicSession tsession;
    private TopicSubscriber tsubscriber;

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                String text = textMessage.getText();
                logger.info("Received message: " + text);
            }
        } catch (JMSException e) {
            logger.severe("Error processing message: " + e.getMessage());
        }
    }

    public void init() throws NamingException, JMSException {
        Context ctx = getInitialContext();
        TopicConnectionFactory tconFactory = (TopicConnectionFactory) PortableRemoteObject.narrow(ctx.lookup(JMS_FACTORY), TopicConnectionFactory.class);
        tcon = tconFactory.createTopicConnection();
        tsession = tcon.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = (Topic) PortableRemoteObject.narrow(ctx.lookup(TOPIC), Topic.class);
        tsubscriber = tsession.createSubscriber(topic);
        tsubscriber.setMessageListener(this);
        tcon.start();
    }

    public void close() throws JMSException {
        tsubscriber.close();
        tsession.close();
        tcon.close();
    }

    private static InitialContext getInitialContext() throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, JNDI_FACTORY);
        env.put(Context.PROVIDER_URL, "t3://localhost:7001");
        env.put("weblogic.jndi.createIntermediateContexts", "true");
        return new InitialContext(env);
    }
}