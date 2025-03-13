package com.vls.tristar.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import groovy.transform.ToString

import java.time.Instant

@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
class Market {
    String id;
    String configId;
    String name;
    String reference;
    String marketGroupName;
    String gameId;
    String roundId;
    String type;
    Integer sortOrder;
    Instant marketOpenTime;
    Instant marketCloseTime;
    List<Selection> selections;
    List<TimeRange> bettingTimes
    String status;

    static class TimeRange {
        Instant start;
        Instant end;
    }

    @ToString
    static class Selection {
        String id;
        String name;
        String reference;
        List<Price> backPrices;
        List<Price> layPrices;
        String status;
    }

    @ToString
    static class Price {
        String id;
        String side;
        BigDecimal value;
    }
}

class Demo {
    static ObjectMapper objectMapper = new ObjectMapper()

    static def main(args) {
        objectMapper.registerModule(new JavaTimeModule())

        String json = "{\"id\":\"67d26e269374204751cc3d54\",\"configId\":\"6710d8dfd8d14709da942c15\",\"name\":\"Tiger Individual Card\",\"reference\":\"TIGER\",\"marketGroupName\":null,\"gameId\":\"1\",\"roundId\":\"67d26e269374204751cc3d4a\",\"type\":\"RANK\",\"sortOrder\":420,\"marketOpenTime\":\"2025-03-13T05:33:26.312Z\",\"marketCloseTime\":\"2025-03-13T05:34:11.554669640Z\",\"bettingTimes\":[{\"start\":\"2025-03-13T05:33:27.304Z\",\"end\":\"2025-03-13T05:34:02.304Z\"}],\"selections\":[{\"id\":\"9d1fca51-928c-4510-a6c4-7431236b0277\",\"name\":\"Ace\",\"reference\":\"A\",\"backPrices\":[{\"id\":\"b66c3589-eead-45eb-9109-af0f9b8e86fb\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"f179c20e-d6d3-44c0-adff-3d43370c90e4\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"e2f208bd-2705-4bb2-aaaf-215a48a9dad2\",\"name\":\"Two\",\"reference\":\"_2\",\"backPrices\":[{\"id\":\"6bfc85b3-b545-4181-b864-d65b5b615cda\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"04b1f40d-c0ac-4beb-b5cb-e6ca2782577f\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"30f1ef7d-bdc1-4055-bf2f-41574001fe76\",\"name\":\"Three\",\"reference\":\"_3\",\"backPrices\":[{\"id\":\"8a608185-5cc7-4fc2-bb37-99615fc4a74b\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"91148b8b-f6b9-49f0-882b-cdf8832e7b66\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"599d4d18-026d-473e-81e8-2fd029796561\",\"name\":\"Four\",\"reference\":\"_4\",\"backPrices\":[{\"id\":\"2e5176c6-5f91-4c72-b974-3e03f2fd9d78\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"84a2c393-d93e-4350-945b-e9bb743719e4\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"c22e6593-647b-4a9f-abf3-4b1bc10c1e05\",\"name\":\"Five\",\"reference\":\"_5\",\"backPrices\":[{\"id\":\"4f9629f8-8090-4c51-b46b-f06cac9b28fd\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"3440b8c5-f47c-4fb9-acde-d38b00c23345\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"WINNER\"},{\"id\":\"3a2f79cb-e445-4bfe-8840-f04b94e47ae9\",\"name\":\"Six\",\"reference\":\"_6\",\"backPrices\":[{\"id\":\"9d8eabb7-9f55-4d1b-94bb-fcde70eabada\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"645a9122-ca1a-460a-8cc5-32413df69a33\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"fd0c9d71-aa49-4768-926a-1a8b61f8ba87\",\"name\":\"Seven\",\"reference\":\"_7\",\"backPrices\":[{\"id\":\"8da6cd20-1059-4705-b4cf-4f9a14149293\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"d2b88ae9-089a-403f-9476-d7c61230bc2f\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"87f7b8da-3ec6-4fdc-9db7-fa6c7958b66a\",\"name\":\"Eight\",\"reference\":\"_8\",\"backPrices\":[{\"id\":\"866178a9-6aa6-45a2-8fd6-38d7bc9190d9\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"bd1890aa-f571-4f6f-a960-b585770818e5\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"e1c569e3-c561-4585-ba0f-e12bf3732257\",\"name\":\"Nine\",\"reference\":\"_9\",\"backPrices\":[{\"id\":\"24fd4e90-06a3-4f29-abd4-07e6ad6e5b21\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"c71e6bf6-8b8c-4905-a91a-61f149f44018\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"2d48fafb-4b5e-4c79-8417-9b77a20fafc3\",\"name\":\"Ten\",\"reference\":\"_10\",\"backPrices\":[{\"id\":\"dd7e3222-ed1e-41ab-9a04-3ace18673cd1\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"8e8dc090-abe1-4219-a7d4-e2bbeb3b30d0\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"6a10ea26-14d9-4d38-ad79-7b9f1d5307e6\",\"name\":\"Jack\",\"reference\":\"J\",\"backPrices\":[{\"id\":\"1450d213-7a48-4573-a38b-46973786eaf6\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"5c654dcd-7936-4d2e-ba9f-4b9b022aef10\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"03ca060d-e6f0-46e4-af88-1d4d34014854\",\"name\":\"Queen\",\"reference\":\"Q\",\"backPrices\":[{\"id\":\"85df00bf-3fd1-4644-812e-dd00d288b4ca\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"a88e69a4-dc38-4450-9cfd-88d43c5c93da\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"},{\"id\":\"62da4dc9-88e1-4518-b270-00346a78c708\",\"name\":\"King\",\"reference\":\"K\",\"backPrices\":[{\"id\":\"634144d8-06a9-42f3-a14c-59bc9eb9c7b9\",\"side\":\"BACK\",\"value\":12}],\"layPrices\":[{\"id\":\"7c478c9a-10e2-4904-811c-b29d3356c845\",\"side\":\"LAY\",\"value\":1.01}],\"status\":\"LOSER\"}],\"status\":\"CLOSED\",\"serverTime\":\"2025-03-13T05:34:11.615189348Z\"}\u0000"

        Market market = objectMapper.readValue(json, Market)
    }
}
