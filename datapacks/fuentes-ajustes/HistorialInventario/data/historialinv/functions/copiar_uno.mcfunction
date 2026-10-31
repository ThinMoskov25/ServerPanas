# Ejecutado AS un jugador: UUID (para saber de quien es), inventario + armadura,
# cofre de Ender y Curios (amuletos, gafas, mochila equipada con su contenido).
data modify storage panelsp:hist uno set value {}
data modify storage panelsp:hist uno.UUID set from entity @s UUID
data modify storage panelsp:hist uno.Inventory set from entity @s Inventory
data modify storage panelsp:hist uno.EnderItems set from entity @s EnderItems
data modify storage panelsp:hist uno.Curios set from entity @s ForgeCaps."curios:inventory"
data modify storage panelsp:hist foto.jugadores append from storage panelsp:hist uno
