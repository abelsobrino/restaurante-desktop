package pe.edu.utp.restaurante.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedidos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 20)
    private String tipo = "LOCAL";

    @Column(nullable = false, length = 20)
    private String origen = "PRESENCIAL";

    @Column(name = "mesa_id")
    private Long mesaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "cliente_nombre")
    private String clienteNombre;

    @Column(name = "cliente_telefono", length = 20)
    private String clienteTelefono;

    @Column(name = "direccion_entrega")
    private String direccionEntrega;

    @Column(length = 20)
    private String estado = "PENDIENTE";

    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "observacion_extra")
    private String observacionExtra;

    private Boolean cerrado = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
}