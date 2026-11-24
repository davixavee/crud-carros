package app;

import java.math.BigDecimal;

public record Carro(int id, String marca, String modelo, int ano, String cor, BigDecimal preco, String imagem) {
}
