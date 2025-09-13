# Master thesis 2018

Source markdown files located at [src](src) directory.
Result is published at [publish](publish) directory.

Source: [src/thesis.md](src/thesis.md)  
Result: [publish/thesis.pdf](publish/thesis.pdf)

## Building

Following command builds your thesis into `.pdf`:

```
make
```

## Publishing

To "publish" the final `.pdf` you can use the following command which builds and copies `src/*.pdf` into `publish/*.pdf`
which can then be committed to the repo:

```
make publish
```

> Note: also converts [src/title.doc](src/title.doc) to pdf and prepends to the resulting artifact.
