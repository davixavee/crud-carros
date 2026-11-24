package app;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CarroDAO {

    private Carro ler(ResultSet rs) throws SQLException {
        return new Carro(
                rs.getInt("id"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getInt("ano"),
                rs.getString("cor"),
                rs.getBigDecimal("preco"),
                rs.getString("imagem"));
    }

    public List<Carro> listar() throws SQLException {
        List<Carro> lista = new ArrayList<>();
        try (Connection c = Conexao.abrir();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM carros ORDER BY id DESC")) {
            while (rs.next()) lista.add(ler(rs));
        }
        return lista;
    }

    public Carro buscar(int id) throws SQLException {
        try (Connection c = Conexao.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM carros WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? ler(rs) : null;
            }
        }
    }

    public void inserir(String marca, String modelo, int ano, String cor, BigDecimal preco, String imagem) throws SQLException {
        String sql = "INSERT INTO carros (marca, modelo, ano, cor, preco, imagem) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = Conexao.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, marca);
            ps.setString(2, modelo);
            ps.setInt(3, ano);
            ps.setString(4, cor);
            ps.setBigDecimal(5, preco);
            ps.setString(6, imagem);
            ps.executeUpdate();
        }
    }

    // Se "imagem" for null, mantém a imagem que já estava salva.
    public void atualizar(int id, String marca, String modelo, int ano, String cor, BigDecimal preco, String imagem) throws SQLException {
        String sql = "UPDATE carros SET marca=?, modelo=?, ano=?, cor=?, preco=?, imagem=COALESCE(?, imagem) WHERE id=?";
        try (Connection c = Conexao.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, marca);
            ps.setString(2, modelo);
            ps.setInt(3, ano);
            ps.setString(4, cor);
            ps.setBigDecimal(5, preco);
            ps.setString(6, imagem);
            ps.setInt(7, id);
            ps.executeUpdate();
        }
    }

    public void excluir(int id) throws SQLException {
        try (Connection c = Conexao.abrir();
             PreparedStatement ps = c.prepareStatement("DELETE FROM carros WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
