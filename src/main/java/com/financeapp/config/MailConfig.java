package com.financeapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Properties;
import java.util.concurrent.Executor;

@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String host;

    @Value("${spring.mail.port:587}")
    private int port;

    @Value("${spring.mail.username:b79592891@gmail.com}")
    private String username;

    @Value("${spring.mail.password:bneksgwaelkzbslg}")
    private String password;

    @Bean
    public JavaMailSender javaMailSender() {
        System.setProperty("java.net.preferIPv4Stack", "true");
        log.info("[AsadovBank MailConfig] JavaMailSender konfiqurasiya edilir: host={}, port={}, systemUsername={}",
                host, port, username);

        String effectiveAuthUser = username != null ? username.replaceAll("\\s+", "") : "";
        String effectiveAuthPass = password != null ? password.replaceAll("\\s+", "") : "";

        if ("bneksgwaelkzbslg".equals(effectiveAuthPass) && "rvans6754@gmail.com".equalsIgnoreCase(effectiveAuthUser)) {
            effectiveAuthUser = "b79592891@gmail.com";
            effectiveAuthPass = "bneksgwaelkzbslg";
            log.info("[AsadovBank MailConfig] SMTP nəqliyyat kanalı aktivləşdirildi (Auth poçtu: {})", effectiveAuthUser);
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(effectiveAuthUser);
        sender.setPassword(effectiveAuthPass);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.debug", "true");
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        // Hostname avtomatik təyin edilsin (EHLO localhost spam filtrlərinə düşməsin)
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
        props.put("mail.smtp.ssl.checkserveridentity", "false");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return sender;
    }

    @Bean(name = "taskExecutor")
    @Primary
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("EmailAsync-");
        executor.initialize();
        return executor;
    }
}
