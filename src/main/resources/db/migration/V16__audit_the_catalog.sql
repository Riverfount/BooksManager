-- RF27: the audit trail also records when a book, an author, a category or a copy is deactivated or reactivated.
-- The actions and the targets are limited by a check constraint, so they are widened here. The trigger of the table
-- refuses an UPDATE of a line, not a change of the table, and the lines that exist are not touched.
alter table audit_log
    drop constraint audit_log_action_check,
    add constraint audit_log_action_check
        check (action in ('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGIN_LOCKOUT', 'USER_CREATED', 'USER_UPDATED',
                          'USER_DEACTIVATED', 'USER_UNBLOCKED', 'USER_PASSWORD_RESET', 'READER_BLOCKED',
                          'READER_UNBLOCKED', 'READER_DEACTIVATED', 'FINE_PAID', 'FINE_WAIVED',
                          'SUSPENSION_ENDED', 'LIBRARY_CONFIGURATION_UPDATED',
                          'BOOK_DEACTIVATED', 'BOOK_REACTIVATED', 'AUTHOR_DEACTIVATED', 'AUTHOR_REACTIVATED',
                          'CATEGORY_DEACTIVATED', 'CATEGORY_REACTIVATED', 'COPY_DEACTIVATED',
                          'COPY_REACTIVATED')),
    drop constraint audit_log_target_type_check,
    add constraint audit_log_target_type_check
        check (target_type in ('USER', 'READER', 'FINE', 'SUSPENSION', 'LIBRARY_CONFIGURATION', 'BOOK', 'AUTHOR',
                               'CATEGORY', 'COPY'));
