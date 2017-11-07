# About

This is simple scraper of the Nacka library API.
It is used to simplify viewing of the current loan and reservation status for multiple accounts.
This can be useful for households with multiple accounts.

# Configuration
```
mkdir data
cp example.conf data/bib.conf
```
Change the example configuration to include your own credentials.

# Usage

Install to your local crontab with `./install.sh`. Verify that it worked by looking at `crontab -l`

Uninstall with `./uninstall.sh`. Verify that it worked by looking at `crontab -l`

Wait for cron to run it or run it manually with:
`./run.sh`

Output is found in the generated `index.html` file.

You can create a custom `upload.sh` file to upload the output to somewhere useful.

# License

See [License](LICENSE.txt)

