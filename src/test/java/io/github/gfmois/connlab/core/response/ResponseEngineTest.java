package io.github.gfmois.connlab.core.response;

import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.model.Message;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.github.gfmois.connlab.support.Configs.*;
import static org.junit.jupiter.api.Assertions.*;

class ResponseEngineTest {
    private final ResponseEngine engine = new ResponseEngine();

    private Optional<String> respond(Configuration configuration, String incoming) {
        return engine.respond(configuration, incoming).map(ResponseEngine.Reply::text);
    }

    @Test
    void buildsResponseFromStaticTextAndRegexGroups() {
        Configuration cfg = configuration(true, helloMessage());

        assertEquals(Optional.of("Bye, Juan!"), respond(cfg, "Hello, Juan"));
    }

    @Test
    void groupZeroIsTheWholeMessage() {
        Configuration cfg = configuration(true, message("echo", ".*", text("echo: "), group(0)));

        assertEquals(Optional.of("echo: ping 42"), respond(cfg, "ping 42"));
    }

    @Test
    void combinesSeveralGroupsInAnyOrder() {
        Configuration cfg = configuration(true,
                message("swap", "^(\\w+)=(\\w+)$", group(2), text("="), group(1)));

        assertEquals(Optional.of("value=key"), respond(cfg, "key=value"));
    }

    @Test
    void regexMustMatchTheWholeMessage() {
        Configuration cfg = configuration(true, message("exact", "Hello", text("hi")));

        assertEquals(Optional.empty(), respond(cfg, "Hello world"));
        assertEquals(Optional.empty(), respond(cfg, "Say Hello"));
        assertEquals(Optional.of("hi"), respond(cfg, "Hello"));
    }

    @Test
    void noMatchingMessageGivesNoResponse() {
        Configuration cfg = configuration(true, helloMessage());

        assertEquals(Optional.empty(), respond(cfg, "Goodbye"));
    }

    @Test
    void firstMatchingMessageWins() {
        Message specific = message("specific", "^PING$", text("PONG"));
        Message generic = message("generic", ".*", text("unknown command"));

        assertEquals(Optional.of("PONG"), respond(configuration(true, specific, generic), "PING"));
        assertEquals(Optional.of("unknown command"), respond(configuration(true, specific, generic), "FOO"));
        assertEquals(Optional.of("unknown command"), respond(configuration(true, generic, specific), "PING"));
    }

    @Test
    void replyCarriesTheMatchedMessage() {
        Message hello = broadcast(helloMessage());
        Configuration cfg = configuration(true, message("other", "^other$", text("x")), hello);

        ResponseEngine.Reply reply = engine.respond(cfg, "Hello, Ana").orElseThrow();

        assertSame(hello, reply.message());
        assertTrue(reply.message().isBroadcast());
    }

    @Test
    void invalidRegexIsSkippedAndTheNextMessagesStillMatch() {
        Configuration cfg = configuration(true,
                message("broken", "([unclosed", text("never")),
                helloMessage());

        assertEquals(Optional.of("Bye, Juan!"), respond(cfg, "Hello, Juan"));
    }

    @Test
    void messagesWithoutRegexAreSkipped() {
        Configuration cfg = configuration(true,
                message("null regex", null, text("never")),
                message("empty regex", "", text("never")),
                helloMessage());

        assertEquals(Optional.of("Bye, Juan!"), respond(cfg, "Hello, Juan"));
    }

    @Test
    void invalidGroupIndexesAreIgnored() {
        Configuration cfg = configuration(true,
                message("bad groups", "^Hello, (.*)$", text("["), group(5), group(-1), group(null), group(1), text("]")));

        assertEquals(Optional.of("[Juan]"), respond(cfg, "Hello, Juan"));
    }

    @Test
    void optionalGroupThatDidNotParticipateIsEmpty() {
        Configuration cfg = configuration(true,
                message("optional", "^cmd( (\\w+))?$", text("arg="), group(2), text(";")));

        assertEquals(Optional.of("arg=x;"), respond(cfg, "cmd x"));
        assertEquals(Optional.of("arg=;"), respond(cfg, "cmd"));
    }

    @Test
    void nullStaticTextIsIgnored() {
        Configuration cfg = configuration(true, message("null text", "^a$", text(null), text("b")));

        assertEquals(Optional.of("b"), respond(cfg, "a"));
    }

    @Test
    void matchingMessageWithEmptyResponseGivesNoResponseAndDoesNotFallThrough() {
        Message silent = message("silent", "^quiet$");
        Message generic = message("generic", ".*", text("fallback"));

        assertEquals(Optional.empty(), respond(configuration(true, silent, generic), "quiet"));
    }

    @Test
    void messageWithNullResponseBuilderGivesNoResponse() {
        Message message = message("no builder", "^a$");
        message.setResponseBuilder(null);

        assertEquals(Optional.empty(), respond(configuration(true, message), "a"));
    }

    @Test
    void nothingToRespondWithoutConfigurationMessagesOrIncomingText() {
        Configuration withoutMessages = configuration(true);
        Configuration nullMessages = configuration(true);
        nullMessages.setMessages(null);
        Configuration cfg = configuration(true, message("any", ".*", text("x")));

        assertEquals(Optional.empty(), respond(null, "Hello"));
        assertEquals(Optional.empty(), respond(withoutMessages, "Hello"));
        assertEquals(Optional.empty(), respond(nullMessages, "Hello"));
        assertEquals(Optional.empty(), respond(cfg, null));
        assertEquals(Optional.empty(), respond(cfg, ""));
    }

    @Test
    void handlesUnicode() {
        Configuration cfg = configuration(true, helloMessage());

        assertEquals(Optional.of("Bye, Ñandú €!"), respond(cfg, "Hello, Ñandú €"));
    }
}
