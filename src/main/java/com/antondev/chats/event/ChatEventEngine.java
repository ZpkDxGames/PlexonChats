package com.antondev.chats.event;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

/** Pure Chat Events engine: generators, answer matching and exact-once run transition. */
public final class ChatEventEngine {
    private ChatEventEngine() { }

    public enum Type { TYPE, UNSCRAMBLE, MATH, TRIVIA, REVERSE }
    public enum MathOperation { ADD, SUBTRACT, MULTIPLY, DIVIDE }
    public enum State { SCHEDULED, ACTIVE, WON, TIMED_OUT, CANCELLED }

    public record Round(UUID runId, ChatEventConfig.Definition definition, Map<String, String> promptValues,
                        List<String> acceptedAnswers) {
        public Round {
            promptValues = Map.copyOf(promptValues);
            acceptedAnswers = List.copyOf(acceptedAnswers);
        }
        public String canonicalAnswer() { return acceptedAnswers.isEmpty() ? "" : acceptedAnswers.getFirst(); }
    }

    public record Winner(UUID playerId, String playerName) { }

    public static final class Competition {
        private final Round round;
        private final long startedNanos;
        private final long deadlineNanos;
        private final Set<UUID> eligiblePlayers;
        private final Set<String> normalizedAnswers;
        private final AtomicReference<State> state = new AtomicReference<>(State.SCHEDULED);
        private final AtomicBoolean rewardAttempted = new AtomicBoolean(false);
        private volatile Winner winner;

        public Competition(Round round, long startedNanos, long deadlineNanos, Set<UUID> eligiblePlayers) {
            this.round = round;
            this.startedNanos = startedNanos;
            this.deadlineNanos = deadlineNanos;
            this.eligiblePlayers = Set.copyOf(eligiblePlayers);
            Set<String> answers = new LinkedHashSet<>();
            for (String answer : round.acceptedAnswers()) answers.add(normalize(answer, round.definition().matching()));
            this.normalizedAnswers = Set.copyOf(answers);
        }
        public boolean activate() { return state.compareAndSet(State.SCHEDULED, State.ACTIVE); }
        public boolean tryWin(UUID playerId, String playerName, String rawAnswer) {
            if (!eligiblePlayers.contains(playerId) || !normalizedAnswers.contains(normalize(rawAnswer, round.definition().matching()))) return false;
            if (!state.compareAndSet(State.ACTIVE, State.WON)) return false;
            winner = new Winner(playerId, playerName);
            return true;
        }
        public boolean timeout() { return state.compareAndSet(State.ACTIVE, State.TIMED_OUT); }
        public boolean cancel() { return state.compareAndSet(State.ACTIVE, State.CANCELLED) || state.compareAndSet(State.SCHEDULED, State.CANCELLED); }
        public boolean beginReward() { return rewardAttempted.compareAndSet(false, true); }
        public Round round() { return round; }
        public long startedNanos() { return startedNanos; }
        public long deadlineNanos() { return deadlineNanos; }
        public Set<UUID> eligiblePlayers() { return eligiblePlayers; }
        public State state() { return state.get(); }
        public Winner winner() { return winner; }
    }

    @FunctionalInterface
    public interface Generator { Round generate(ChatEventConfig.Definition definition, RandomGenerator random); }

    public static Map<Type, Generator> generators() {
        EnumMap<Type, Generator> values = new EnumMap<>(Type.class);
        values.put(Type.TYPE, ChatEventEngine::type);
        values.put(Type.UNSCRAMBLE, ChatEventEngine::unscramble);
        values.put(Type.MATH, ChatEventEngine::math);
        values.put(Type.TRIVIA, ChatEventEngine::trivia);
        values.put(Type.REVERSE, ChatEventEngine::reverse);
        return Collections.unmodifiableMap(values);
    }

    public static String normalize(String source, ChatEventConfig.Matching matching) {
        String value = source == null ? "" : Normalizer.normalize(source, matching.normalization());
        if (matching.trim()) value = value.strip();
        if (matching.collapseWhitespace()) value = value.replaceAll("\\s+", " ");
        if (matching.ignoreDiacritics()) {
            String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
            value = decomposed.replaceAll("\\p{M}+", "");
            value = Normalizer.normalize(value, matching.normalization());
        }
        if (!matching.caseSensitive()) value = value.toLowerCase(Locale.ROOT);
        return value;
    }

    private static Round type(ChatEventConfig.Definition definition, RandomGenerator random) {
        String value = pick(definition.values(), random);
        return round(definition, Map.of("value", value), List.of(value));
    }

    private static Round unscramble(ChatEventConfig.Definition definition, RandomGenerator random) {
        String value = pick(definition.words(), random);
        String scrambled = scramble(value, random);
        return round(definition, Map.of("value", value, "scrambled", scrambled), List.of(value));
    }

    private static Round trivia(ChatEventConfig.Definition definition, RandomGenerator random) {
        ChatEventConfig.TriviaEntry entry = pick(definition.trivia(), random);
        return round(definition, Map.of("question", entry.question()), entry.acceptedAnswers());
    }

    private static Round reverse(ChatEventConfig.Definition definition, RandomGenerator random) {
        String value = pick(definition.values(), random);
        int[] codePoints = value.codePoints().toArray();
        StringBuilder reversed = new StringBuilder();
        for (int i = codePoints.length - 1; i >= 0; i--) reversed.appendCodePoint(codePoints[i]);
        return round(definition, Map.of("value", value), List.of(reversed.toString()));
    }

    private static Round math(ChatEventConfig.Definition definition, RandomGenerator random) {
        List<MathOperation> operations = new ArrayList<>(definition.mathOperations());
        MathOperation operation = pick(operations, random);
        long left;
        long right;
        long answer;
        switch (operation) {
            case ADD -> {
                left = operand(definition, random); right = operand(definition, random); answer = Math.addExact(left, right);
            }
            case SUBTRACT -> {
                left = operand(definition, random); right = operand(definition, random);
                if (!definition.allowNegativeResult() && left < right) { long swap = left; left = right; right = swap; }
                answer = Math.subtractExact(left, right);
            }
            case MULTIPLY -> {
                left = operand(definition, random); right = operand(definition, random); answer = Math.multiplyExact(left, right);
            }
            case DIVIDE -> {
                long[] pair = exactDivisionOperands(definition, random);
                left = pair[0]; right = pair[1]; answer = left / right;
            }
            default -> throw new IllegalStateException("Unhandled math operation " + operation);
        }
        String symbol = switch (operation) { case ADD -> "+"; case SUBTRACT -> "−"; case MULTIPLY -> "×"; case DIVIDE -> "÷"; };
        String expression = left + " " + symbol + " " + right;
        return round(definition, Map.of("expression", expression), List.of(Long.toString(answer)));
    }

    private static long[] exactDivisionOperands(ChatEventConfig.Definition definition, RandomGenerator random) {
        for (int attempt = 0; attempt < 48; attempt++) {
            long left = operand(definition, random);
            long right = operand(definition, random);
            if (right != 0 && left % right == 0) return new long[]{left, right};
        }
        long value = firstNonZero(definition.minOperand(), definition.maxOperand());
        return new long[]{value, value};
    }

    private static long firstNonZero(int min, int max) {
        if (min <= 1 && max >= 1) return 1;
        if (min <= -1 && max >= -1) return -1;
        if (min != 0) return min;
        if (max != 0) return max;
        throw new IllegalArgumentException("DIVIDE range contains no non-zero operand");
    }

    private static long operand(ChatEventConfig.Definition definition, RandomGenerator random) {
        long min = definition.minOperand();
        long max = definition.maxOperand();
        if (min == max) return min;
        return random.nextLong(min, max + 1L);
    }

    private static String scramble(String source, RandomGenerator random) {
        int[] original = source.codePoints().toArray();
        if (original.length < 2) return source;
        boolean allSame = true;
        for (int i = 1; i < original.length; i++) if (original[i] != original[0]) { allSame = false; break; }
        if (allSame) return source;
        for (int attempt = 0; attempt < 8; attempt++) {
            int[] copy = original.clone();
            for (int i = copy.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int swap = copy[i]; copy[i] = copy[j]; copy[j] = swap;
            }
            String result = fromCodePoints(copy);
            if (!result.equals(source)) return result;
        }
        int[] copy = original.clone();
        int firstDifferent = 1;
        while (firstDifferent < copy.length && copy[firstDifferent] == copy[0]) firstDifferent++;
        int swap = copy[0]; copy[0] = copy[firstDifferent]; copy[firstDifferent] = swap;
        return fromCodePoints(copy);
    }

    private static String fromCodePoints(int[] values) {
        StringBuilder result = new StringBuilder();
        for (int value : values) result.appendCodePoint(value);
        return result.toString();
    }

    private static Round round(ChatEventConfig.Definition definition, Map<String, String> values, List<String> answers) {
        return new Round(UUID.randomUUID(), definition, new LinkedHashMap<>(values), answers);
    }

    private static <T> T pick(List<T> values, RandomGenerator random) {
        if (values.isEmpty()) throw new IllegalArgumentException("Cannot choose from an empty event data set");
        return values.get(random.nextInt(values.size()));
    }
}
