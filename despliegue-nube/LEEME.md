# Panel en la nube (24/7 sin tu PC)

El panel funciona igual que en tu PC: mismas pantallas, mismos usuarios y el mismo enlace fijo (`https://preabstract-kole-sauceless.ngrok-free.dev`).

## Lo único que haces tú: crear el servidor gratuito en Oracle Cloud

1. Regístrate en https://www.oracle.com/cloud/free/. Piden tarjeta solo para verificar identidad; el plan *Always Free* no cobra.
2. En la consola: **Compute → Instances → Create instance**.
   - **Image:** Canonical Ubuntu 22.04 o 24.04.
   - **Shape:** cualquiera marcada como *Always Free eligible* (Ampere A1 o VM.Standard.E2.1.Micro).
   - **Add SSH keys → Paste public keys**, y pega esta línea completa:

     ```
     ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHaX8Ew7SBPdboFoLpUkpOdPtSBUU0ulB91A6Zpcd5Il serverpanas-nube
     ```
3. Pulsa **Create**. Cuando esté en verde (*Running*), copia la **Public IP address** y pásamela.

No hace falta abrir puertos: el panel sale a Internet por ngrok.

## Lo que hago yo con la IP

```powershell
.\desplegar.ps1 -IP <la IP pública>
```

1. Copia el panel y tus usuarios al servidor.
2. Configura los secretos (token de exaroton y de ngrok) en un archivo que solo lee el servicio.
3. Deja el panel y el túnel como servicios que arrancan solos y se reinician si fallan.
4. Comprueba el enlace desde Internet.
5. Desactiva el panel de tu PC (si algo falla, lo deja como estaba).
