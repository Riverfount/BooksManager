package com.riverfount.booksmanager.loan.adapter.in.web;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads a whole number and refuses anything else. Jackson's default cuts a decimal
 * like 1.5 to 1, which would silently save a value the caller did not send; here
 * it is a 400, in the spirit of "never adjust silently" of the API. Used by the
 * configuration request, whose fields are limits and periods.
 */
class WholeNumberDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return parser.getIntValue();
        }
        return (Integer) context.handleUnexpectedToken(Integer.class, parser);
    }
}
