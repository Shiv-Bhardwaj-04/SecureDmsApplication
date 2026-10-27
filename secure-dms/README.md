# Secure DMS

Secure DMS is a Spring Boot web application. Run it locally with Java 25:

```powershell
cd secure-dms
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080`. The local demo creates these accounts, all using
`Demo@123` unless `DMS_DEMO_PASSWORD` is set:

- `anita@dms.gov.in` (admin)
- `priya@dms.gov.in` (IO)
- `karan@dms.gov.in` (reviewer)
- `vikram@dms.gov.in` (auditor)

## Deploy a Demo

This repository includes a Render Blueprint (`render.yaml`) and a Dockerfile.
To deploy:

1. Push this repository to GitHub. The root `.gitignore` excludes local databases,
	uploads, and key files; review `git status` before committing.
2. In Render, choose **New + > Blueprint**, connect the GitHub repository, and
	apply the blueprint.
3. When prompted, set `DMS_DEMO_PASSWORD` to a strong, unique password. All four
	demo accounts use this password.
4. Wait for the first build to finish, then open the Render URL shown for the
	service. The persistent disk keeps the demo database, uploaded files, and
	locally generated encryption keys across restarts.

This is a demo deployment, not a production records system. Do not upload real
or confidential documents: the demo accounts share one password, and the app
uses local H2 and file-based keys rather than managed production database,
storage, and key services. The Render service uses a persistent disk and a
paid instance plan so its data survives restarts; hosting charges may apply.