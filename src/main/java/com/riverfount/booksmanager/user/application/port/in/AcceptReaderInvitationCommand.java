package com.riverfount.booksmanager.user.application.port.in;

/** The reader accepts an invitation by choosing a login and a password. The token comes from the e-mail link. */
public record AcceptReaderInvitationCommand(String token, String login, String password) {

    @Override
    public String toString() {
        return "AcceptReaderInvitationCommand[token=***, login=" + login + ", password=***]";
    }
}
