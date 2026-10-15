package com.riverfount.booksmanager.user.domain;

/**
 * Guardian of a reader under 18 (RNF11): immutable value object. The name
 * and e-mail are required; the phone is optional.
 */
public record Guardian(String name, String email, String phone) {

    private static final String SUBJECT = "do responsável";

    public Guardian {
        PersonalData.validateName(name, SUBJECT);
        PersonalData.validateEmail(email, SUBJECT);
        PersonalData.validateOptionalPhone(phone, SUBJECT);
    }
}
