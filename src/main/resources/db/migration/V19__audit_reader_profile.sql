-- RF32, RNF32: the audit trail also records when a reader changes their own contact data or password. The actions are
-- limited by a check constraint, so it is widened here (the targets READER and USER already exist). The trigger of the
-- table refuses an UPDATE of a line, not a change of the table, and the lines that exist are not touched.
alter table audit_log
    drop constraint audit_log_action_check,
    add constraint audit_log_action_check
        check (action in ('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGIN_LOCKOUT', 'USER_CREATED', 'USER_UPDATED',
                          'USER_DEACTIVATED', 'USER_UNBLOCKED', 'USER_PASSWORD_RESET', 'READER_BLOCKED',
                          'READER_UNBLOCKED', 'READER_DEACTIVATED', 'FINE_PAID', 'FINE_WAIVED',
                          'SUSPENSION_ENDED', 'LIBRARY_CONFIGURATION_UPDATED',
                          'BOOK_DEACTIVATED', 'BOOK_REACTIVATED', 'AUTHOR_DEACTIVATED', 'AUTHOR_REACTIVATED',
                          'CATEGORY_DEACTIVATED', 'CATEGORY_REACTIVATED', 'COPY_DEACTIVATED',
                          'COPY_REACTIVATED', 'READER_ACCESS_INVITED', 'READER_ACCESS_GRANTED',
                          'READER_ACCESS_REVOKED', 'READER_PASSWORD_RESET', 'RESERVATION_CANCELLED',
                          'READER_PROFILE_UPDATED', 'READER_PASSWORD_CHANGED'));
