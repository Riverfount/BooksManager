package com.riverfount.booksmanager.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TitleCaseTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "O NASCIMENTO DA INTELIGÊNCIA NA CRIANÇA | O Nascimento da Inteligência na Criança",
            "JEAN PIAGET | Jean Piaget",
            "MACHADO DE ASSIS | Machado de Assis",
            "DOM CASMURRO | Dom Casmurro",
            "O SENHOR DOS ANÉIS | O Senhor dos Anéis",
            "ÁGUAS E ÉPOCAS | Águas e Épocas"
    })
    void shouldTitleCaseAllCapsTextKeepingConnectivesLowercase(String input, String expected) {
        assertThat(TitleCase.normalize(input)).isEqualTo(expected);
    }

    @Test
    void shouldCapitalizeConnectiveWhenItStartsTheText() {
        assertThat(TitleCase.normalize("DE VOLTA AO LAR")).isEqualTo("De Volta ao Lar");
    }

    @Test
    void shouldCapitalizeFirstWordAfterColon() {
        assertThat(TitleCase.normalize("PSICOLOGIA: A CRIANÇA E O JOGO"))
                .isEqualTo("Psicologia: A Criança e o Jogo");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "REI ARTHUR II | Rei Arthur II",
            "LUÍS XIV | Luís XIV",
            "VOLUME IV DA COLEÇÃO | Volume IV da Coleção",
            "CAPÍTULO XIX | Capítulo XIX"
    })
    void shouldKeepRomanNumeralsUppercase(String input, String expected) {
        assertThat(TitleCase.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "DI CAPRIO | Di Caprio",
            "MIX | Mix",
            "O GRANDE MIX | O Grande Mix",
            "LI | Li"
    })
    void shouldNotTreatCommonWordsOrNamesAsRomanNumerals(String input, String expected) {
        assertThat(TitleCase.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "J.R.R. TOLKIEN | J.R.R. Tolkien",
            "C.S. LEWIS | C.S. Lewis",
            "J. K. ROWLING | J. K. Rowling"
    })
    void shouldKeepInitialsUppercase(String input, String expected) {
        assertThat(TitleCase.normalize(input)).isEqualTo(expected);
    }

    @Test
    void shouldNotTreatAbbreviationWithDotAsInitials() {
        assertThat(TitleCase.normalize("MARTIN LUTHER KING JR.")).isEqualTo("Martin Luther King Jr.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "D'ÁVILA | D'Ávila",
            "O'BRIEN | O'Brien",
            "GUIMARÃES-ROSA | Guimarães-Rosa",
            "JOÃO GUIMARÃES ROSA | João Guimarães Rosa"
    })
    void shouldCapitalizeAfterHyphenAndApostrophe(String input, String expected) {
        assertThat(TitleCase.normalize(input)).isEqualTo(expected);
    }

    @Test
    void shouldSkipLeadingPunctuationWhenCapitalizing() {
        assertThat(TitleCase.normalize("MEMÓRIAS (ORGANIZADOR)")).isEqualTo("Memórias (Organizador)");
    }

    @Test
    void shouldApplyKnownSpellings() {
        assertThat(TitleCase.normalize("RONALD MCDONALD")).isEqualTo("Ronald McDonald");
        assertThat(TitleCase.normalize("A HISTÓRIA DA ONU E DOS EUA")).isEqualTo("A História da ONU e dos EUA");
    }

    @Test
    void shouldKeepSpacingBetweenWords() {
        assertThat(TitleCase.normalize("JEAN  PIAGET")).isEqualTo("Jean  Piaget");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ronald McDonald",
            "Livro II",
            "o nascimento da inteligência",
            "O Nascimento da Inteligência",
            "J.R.R. Tolkien",
            "Piaget"
    })
    void shouldReturnMixedOrLowerCaseTextUnchanged(String input) {
        assertThat(TitleCase.normalize(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "1984", "2001 - 2010", "!!!"})
    void shouldReturnTextWithoutLettersUnchanged(String input) {
        assertThat(TitleCase.normalize(input)).isEqualTo(input);
    }

    @Test
    void shouldReturnNullForNull() {
        assertThat(TitleCase.normalize(null)).isNull();
    }

    @Test
    void shouldBeIdempotent() {
        var once = TitleCase.normalize("O NASCIMENTO DA INTELIGÊNCIA NA CRIANÇA");

        assertThat(TitleCase.normalize(once)).isEqualTo(once);
    }
}
