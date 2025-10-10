# **1\. The Growing Importance of Tagged PDF**

Tagged PDF is a document structure that includes logical information about the content (e.g., headings, paragraphs, lists, tables). While it has long been a standard for accessibility for users with visual impairments, it is now becoming critical for AI understanding. A properly tagged PDF provides a machine-readable map of the document, allowing AI models to accurately interpret its hierarchy and context, which is essential for high-quality data extraction.

European Accessibility Act: The use of accessible digital documents, including PDFs, is a legal requirement in many regions, driving the widespread adoption of Tagged PDF.  
AI-Ready Data: Tagged PDFs transform unstructured content into a rich, semantic structure that AI models can process more effectively than raw text.  
opendataloader-pdf is actively developing technology to leverage these tags, ensuring our engine can deliver superior, contextually aware data.

# **2\. The Challenge: Flawed Tags & Missing Standards**

Despite its importance, the quality of existing Tagged PDFs varies widely. Most are generated automatically with errors or missing information, making them unreliable for both accessibility tools and AI systems. These flawed tags can be more detrimental than having no tags at all.

Validation Gap: The lack of a standardized validation process to ensure tags are accurate and meaningful is a major problem.  
Lost Context: Flawed tags can cause an AI to misinterpret a document's logical flow, confusing titles with paragraphs or misreading table data.

## **2.1. A Collaborative Solution**

This challenge presents a unique opportunity for innovation. Hancom and Dual Lab are collaborating with the PDF Association to drive a solution.

| Organization | Role |
| :---- | :---- |
| PDF Association | Define Well-Tagged PDF specification and Tagged PDF Best practice guide aimed for accessibility and reuse in other workflows including AI. |
| Dual Lab | Develop a veraPDF-based validator to verify if PDFs comply with existing and future standards and recommendations. |
| Hancom | Build OpenDataLoader-PDF's extraction engine to effectively use the validated tags. |

# **3\. Our Vision: Leading the Tagged PDF Revolution**

Our vision is to not only be the first to develop a robust Tagged PDF data extraction tool for AI reuse but also to actively contribute to the global standards that govern it. By working with the PDF Association, we aim to ensure the reliability and integrity of Tagged PDF, turning it into a powerful and trustworthy asset for the entire AI ecosystem.

## **3.1. Available Tagged PDF Filters**

| Filter Name | Defense Purpose | Status |
| :---- | :---- | :---- |
| tagged | Defends against flaws in existing tags and ensures the integrity of the document’s logical structure. | 🕖 In progress |
| tag-validation | A new engine module that validates Tagged PDFs against recommendations of PDF Association. | 🕖 In progress |
| extraction-logic | Develops new extraction methods that prioritize Tagged PDF structure over visual cues for enhanced accuracy. | 🕖 In progress |

## **3.2. Real-World Scenarios**

Research Papers: A well-tagged paper allows an AI to accurately identify the author's name and affiliation as "heading" and "metadata," enabling automated citation building.  
Financial Reports: In a financial report, proper tags enable an AI to precisely extract the title of a balance sheet and its corresponding data cells, automating analysis without relying on error-prone heuristics.  
Legal Contracts: An AI could use tags to quickly identify and cross-reference specific clauses, dates, and parties in a contract, dramatically speeding up the legal review process.  
