package com.riverfount.booksmanager.user.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables the settings of the user module. */
@Configuration
@EnableConfigurationProperties({LoginLockoutProperties.class, ReaderAccessProperties.class})
public class UserConfiguration {
}
