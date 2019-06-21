# docker-docx-pdf-file-converter
Convert a file from DOCX to PDF using ALEPHDATA converter

# TODO
1. Fix the HttpRequest response. Further inspection required to determine cause of "Unexpected end of file from server"
caused by "CRLF expected at end of chunk"
2. Convert convert-document port setting to configurable value (either using @Value or a configuration file)
3. Consider changing the URL and input params to align with convert-document
4. Add Unit Tests
5. Checks/controls on input (either through tomcat or in code) and resource usage
6. Adapt to become a Producer type in the Spring Data Flow structure to receive:
   a. Http Request
   b. File input
   c. FTP input