package online.itlab.springframework.validation.errors.standard.factory.tools;

public class StringTools implements IStringTools {
    @Override
    public String lastSegment(final String input, final char separator) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        int idx = input.lastIndexOf(separator);
        return idx == -1 ? input : input.substring(idx + 1);
    }
}
