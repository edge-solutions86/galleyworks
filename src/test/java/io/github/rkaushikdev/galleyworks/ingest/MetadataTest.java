package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Chamber;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MetadataTest {

    private static final List<String> FRONT = List.of(
            "2025",
            "",
            "STATE OF WYOMING                                   25LSO-0142",
            "",
            "HOUSE BILL NO. HB0142",
            "",
            "Water rights; publication schedule.",
            "",
            "Sponsored by: Joint Agriculture Committee");

    @Test
    void readsThePrintedIdentity() {
        BillHeader header = Metadata.readHeader(FRONT);
        assertThat(header.number()).contains("HB0142");
        assertThat(header.chamber()).contains(Chamber.LOWER);
        assertThat(header.session()).contains("2025");
        assertThat(header.sponsor()).contains("Joint Agriculture Committee");
        assertThat(header.isComplete()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "HOUSE BILL NO. HB0142, LOWER",
        "SENATE FILE NO. SF0032, UPPER",
        "SENATE BILL NO. 14, UPPER",
        "COUNCIL BILL NO. 2024-31, COUNCIL",
        "ORDINANCE NO. 1188, COUNCIL",
        "EXECUTIVE ORDER NO. 2025-04, EXECUTIVE"
    })
    void chamberWords(String line, Chamber chamber) {
        assertThat(Metadata.readHeader(List.of(line)).chamber()).contains(chamber);
    }

    @Test
    void numberStripsSpacesAndDashes() {
        assertThat(Metadata.readHeader(List.of("COUNCIL BILL NO. 2024-31")).number())
                .contains("202431");
        assertThat(Metadata.readHeader(List.of("SENATE FILE NO. SF 0032")).number())
                .contains("SF0032");
    }

    @Test
    void theFirstChamberWordWins() {
        BillHeader header =
                Metadata.readHeader(List.of("HOUSE BILL NO. HB0142", "referred to the SENATE FILE calendar"));
        assertThat(header.chamber()).contains(Chamber.LOWER);
        assertThat(header.number()).contains("HB0142");
    }

    @Test
    void aNumberWithoutAChamberIsIgnored() {
        assertThat(Metadata.readHeader(List.of("DOCKET NO. 1234")).number()).isEmpty();
    }

    @Test
    void blankHeaderIsIncomplete() {
        BillHeader header = Metadata.readHeader(List.of("", "   "));
        assertThat(header).isEqualTo(BillHeader.EMPTY);
        assertThat(header.isComplete()).isFalse();
    }

    @Test
    void sessionTakesTheFirstFourDigitYear() {
        assertThat(Metadata.readHeader(List.of("2025 GENERAL SESSION", "amends the 1977 act"))
                        .session())
                .contains("2025");
    }

    @Test
    void aYearLikeNumberIsNotConfusedWithASession() {
        assertThat(Metadata.readHeader(List.of("HOUSE BILL NO. HB0142")).session())
                .isEmpty();
    }

    @Test
    void sponsorIsTrimmed() {
        assertThat(Metadata.readHeader(List.of("Sponsored by:    Senator Case  "))
                        .sponsor())
                .contains("Senator Case");
    }

    @Test
    void effectiveDateIsParsed() {
        assertThat(Metadata.parseEffectiveDate("This act is effective July 1, 2025."))
                .contains(LocalDate.of(2025, 7, 1));
    }

    @Test
    void effectiveDateIsCaseInsensitive() {
        assertThat(Metadata.parseEffectiveDate("EFFECTIVE MARCH 15, 2026")).contains(LocalDate.of(2026, 3, 15));
    }

    @Test
    void effectiveDateReturnsNoneWhenAbsent() {
        assertThat(Metadata.parseEffectiveDate("This act takes effect immediately."))
                .isEmpty();
    }

    @Test
    void impossibleDatesAreRejected() {
        assertThat(Metadata.parseEffectiveDate("effective February 30, 2025")).isEmpty();
        assertThat(Metadata.parseEffectiveDate("effective Smarch 3, 2025")).isEmpty();
    }
}
