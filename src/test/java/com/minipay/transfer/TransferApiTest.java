package com.minipay.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end tests through HTTP: controller, validation, service, JPA and the real schema (on H2). */
@SpringBootTest
@AutoConfigureMockMvc
class TransferApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void createAccountReturns201WithZeroBalance() throws Exception {
        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Alice\",\"currency\":\"EUR\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.balance").value("0.00"))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void invalidCurrencyIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Alice\",\"currency\":\"euro\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferMovesMoneyAndWritesTwoLedgerEntries() throws Exception {
        UUID alice = openAccount("Alice", "EUR");
        UUID bob = openAccount("Bob", "EUR");
        deposit(alice, "100.00");

        transfer(UUID.randomUUID().toString(), alice, bob, "30.25")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("30.25"));

        expectBalance(alice, "69.75");
        expectBalance(bob, "30.25");
        mvc.perform(get("/api/v1/accounts/{id}/entries", bob))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("CREDIT"));
        mvc.perform(get("/api/v1/accounts/{id}/entries", alice))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder("DEPOSIT", "DEBIT")));
    }

    @Test
    void replayWithSameKeyAndPayloadReturnsSameTransferWithoutMovingMoneyTwice() throws Exception {
        UUID alice = openAccount("Alice", "EUR");
        UUID bob = openAccount("Bob", "EUR");
        deposit(alice, "50.00");
        String key = UUID.randomUUID().toString();

        String firstId = idOf(transfer(key, alice, bob, "20.00").andExpect(status().isCreated()));
        String replayId = idOf(transfer(key, alice, bob, "20.00")
                .andExpect(status().isOk())
                .andExpect(header().string(TransferController.REPLAYED_HEADER, "true")));

        assertThat(replayId).isEqualTo(firstId);
        expectBalance(alice, "30.00");
        expectBalance(bob, "20.00");
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejectedWith409() throws Exception {
        UUID alice = openAccount("Alice", "EUR");
        UUID bob = openAccount("Bob", "EUR");
        deposit(alice, "50.00");
        String key = UUID.randomUUID().toString();

        transfer(key, alice, bob, "20.00").andExpect(status().isCreated());
        transfer(key, alice, bob, "25.00").andExpect(status().isConflict());

        expectBalance(alice, "30.00");
    }

    @Test
    void insufficientFundsIsRejectedWith422AndNothingChanges() throws Exception {
        UUID alice = openAccount("Alice", "EUR");
        UUID bob = openAccount("Bob", "EUR");
        deposit(alice, "10.00");

        transfer(UUID.randomUUID().toString(), alice, bob, "10.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business rule violated"));

        expectBalance(alice, "10.00");
        expectBalance(bob, "0.00");
    }

    @Test
    void currencyMismatchIsRejectedWith422() throws Exception {
        UUID alice = openAccount("Alice", "EUR");
        UUID karim = openAccount("Karim", "MAD");
        deposit(alice, "10.00");

        transfer(UUID.randomUUID().toString(), alice, karim, "5.00")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void missingIdempotencyKeyIsRejectedWith400() throws Exception {
        UUID alice = openAccount("Alice", "EUR");
        UUID bob = openAccount("Bob", "EUR");

        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(alice, bob, "1.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidAmountsAreRejectedWith400() throws Exception {
        UUID alice = openAccount("Alice", "EUR");
        UUID bob = openAccount("Bob", "EUR");
        deposit(alice, "10.00");

        transfer(UUID.randomUUID().toString(), alice, bob, "0.00").andExpect(status().isBadRequest());
        transfer(UUID.randomUUID().toString(), alice, bob, "-1.00").andExpect(status().isBadRequest());
        transfer(UUID.randomUUID().toString(), alice, bob, "1.001").andExpect(status().isBadRequest());
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        mvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiDocumentIsPublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("minipay-ledger API"));
    }

    // ---- helpers ----

    private UUID openAccount(String owner, String currency) throws Exception {
        String body = mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"" + owner + "\",\"currency\":\"" + currency + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void deposit(UUID account, String amount) throws Exception {
        mvc.perform(post("/api/v1/accounts/{id}/deposits", account).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions transfer(String key, UUID from, UUID to, String amount) throws Exception {
        return mvc.perform(post("/api/v1/transfers")
                .header(TransferController.IDEMPOTENCY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody(from, to, amount)));
    }

    private static String transferBody(UUID from, UUID to, String amount) {
        return "{\"fromAccountId\":\"" + from + "\",\"toAccountId\":\"" + to + "\",\"amount\":" + amount + "}";
    }

    private void expectBalance(UUID account, String expected) throws Exception {
        mvc.perform(get("/api/v1/accounts/{id}", account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(expected));
    }

    private String idOf(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asText();
    }
}
