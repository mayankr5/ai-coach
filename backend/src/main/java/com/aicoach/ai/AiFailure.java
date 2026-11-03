package com.aicoach.ai;

import com.google.genai.errors.ApiException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.RestClientResponseException;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.DateTimeException;
import java.util.concurrent.TimeoutException;

/** Why a model call failed, which decides whether to retry the same model or move to the next one. */
public record AiFailure(Kind kind, String reason) {

    public enum Kind {
        /** No answer in time; a retry would likely be just as slow, so move on. */
        TIMEOUT(false),
        /** Server error or dropped connection; worth one more try after a short pause. */
        TEMPORARY(true),
        /** Quota used up; it won't come back within seconds, so move on. */
        RATE_LIMITED(false),
        /** Bad key, unknown model, provider not running: will never work, so move on. */
        PERMANENT(false),
        /** The model answered but the answer can't be read or breaks the rules; retry with feedback. */
        BAD_ANSWER(true);

        public final boolean retrySameModel;

        Kind(boolean retrySameModel) {
            this.retrySameModel = retrySameModel;
        }
    }

    static AiFailure classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException || t instanceof HttpTimeoutException || t instanceof InterruptedIOException) {
                return new AiFailure(Kind.TIMEOUT, "no answer in time");
            }
            if (t instanceof tools.jackson.core.JacksonException || t instanceof com.fasterxml.jackson.core.JacksonException
                    || t instanceof DateTimeException) {
                return new AiFailure(Kind.BAD_ANSWER, "answer could not be read: " + shortMessage(rootCause(t)));
            }
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return new AiFailure(Kind.PERMANENT, "provider unreachable");
            }
            if (t instanceof ApiException api) {
                return byStatus(api.code(), api.getMessage());
            }
            if (t instanceof RestClientResponseException http) {
                return byStatus(http.getStatusCode().value(), http.getMessage());
            }
            if (t instanceof TransientAiException) {
                return new AiFailure(Kind.TEMPORARY, shortMessage(t));
            }
            if (t instanceof NonTransientAiException) {
                return new AiFailure(t.getMessage() != null && t.getMessage().contains("429")
                        ? Kind.RATE_LIMITED : Kind.PERMANENT, shortMessage(t));
            }
            // Other provider SDKs (Anthropic, OpenAI) name their exceptions after the problem
            String name = t.getClass().getSimpleName();
            if (name.contains("RateLimit")) return new AiFailure(Kind.RATE_LIMITED, shortMessage(t));
            if (name.contains("Timeout")) return new AiFailure(Kind.TIMEOUT, "no answer in time");
            if (name.contains("InternalServer") || name.contains("Unavailable") || name.contains("Overloaded")) {
                return new AiFailure(Kind.TEMPORARY, shortMessage(t));
            }
        }
        // Unknown problems: don't spend the user's time retrying something we don't understand
        return new AiFailure(Kind.PERMANENT, shortMessage(error));
    }

    private static AiFailure byStatus(int status, String message) {
        Kind kind = status == 429 ? Kind.RATE_LIMITED : status >= 500 ? Kind.TEMPORARY : Kind.PERMANENT;
        return new AiFailure(kind, "HTTP " + status + ": " + shortMessage(message));
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t;
    }

    private static String shortMessage(Throwable t) {
        return shortMessage(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
    }

    private static String shortMessage(String message) {
        String oneLine = message == null ? "" : message.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
    }
}
