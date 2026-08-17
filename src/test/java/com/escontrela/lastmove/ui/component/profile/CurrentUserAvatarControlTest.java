package com.escontrela.lastmove.ui.component.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CurrentUserAvatarControlTest {

    @Test
    void derivesMonogramFromFirstAndLastName() {
        assertEquals("DP", CurrentUserAvatarText.initialsFor("David Pereira"));
    }

    @Test
    void usesOneInitialForSingleNameAndQuestionMarkForUnknownUser() {
        assertEquals("D", CurrentUserAvatarText.initialsFor("David"));
        assertEquals("?", CurrentUserAvatarText.initialsFor("unknown"));
        assertEquals("?", CurrentUserAvatarText.initialsFor(" "));
    }
}
