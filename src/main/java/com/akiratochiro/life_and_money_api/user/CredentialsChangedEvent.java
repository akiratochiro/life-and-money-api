package com.akiratochiro.life_and_money_api.user;

/** A senha do usuário mudou, ou o usuário deixou de existir: os access tokens dele não valem mais. */
record CredentialsChangedEvent(Long userId) {
}
