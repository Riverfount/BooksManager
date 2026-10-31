-- The prefix of the asset codes the library suggests (PAT-00001): 3 to 4 uppercase letters, chosen by the library in the
-- configuration (RF26, RN16). The default only seeds the existing single row with the usual prefix; the code never
-- falls back to a constant. The check is the last line of defense: the domain validates the same rule with a message
-- for the user.

alter table library_configuration
    add column asset_code_prefix varchar(4) not null default 'PAT'
        constraint library_configuration_asset_code_prefix_format check (asset_code_prefix ~ '^[A-Z]{3,4}$');
