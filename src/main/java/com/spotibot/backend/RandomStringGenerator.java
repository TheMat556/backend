package com.spotibot.backend;

import lombok.NoArgsConstructor;

import java.util.Random;

@NoArgsConstructor
public class RandomStringGenerator {

    private static final String ALLOWED_CHAR_LIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789";

    /**
     Generates a random string identifier of a specified length.
     @param stringLength the desired length of the identifier
     @return a random string identifier of the specified length
     */
    public String generateRandomIdentifier (int stringLength) {

        StringBuilder randStr = new StringBuilder();
        for(int i = 0; i < stringLength; i++) {
            int randomNumber = generateRandomNumber();
            char ch = ALLOWED_CHAR_LIST.charAt(randomNumber);
            randStr.append(ch);
        }

        return randStr.toString();
    }

    /**
     * Generates a random integer within the bounds of the length of a character list.
     * @return the generated random integer
     */
    public int generateRandomNumber() {
        Random randomGenerator = new Random();
        int random = randomGenerator.nextInt(ALLOWED_CHAR_LIST.length());

        if(random == 0)
        {
            return random;
        }
        else
        {
            return random - 1;
        }
    }
}
