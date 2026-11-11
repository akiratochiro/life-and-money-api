package com.akiratochiro.life_and_money_api.invoice;

/** Um arquivo recebido, já fora do HTTP: o nome que o navegador mandou e os bytes. */
record Upload(String filename, byte[] content) {
}
