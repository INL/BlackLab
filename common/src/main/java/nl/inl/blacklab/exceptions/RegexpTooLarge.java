package nl.inl.blacklab.exceptions;

public class RegexpTooLarge extends InvalidQuery {

    public RegexpTooLarge() {
        super("Regular expression too large.");
    }

}
