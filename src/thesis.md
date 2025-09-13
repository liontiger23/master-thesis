---
documentclass: extarticle
papersize: a4
geometry: "left=3cm,right=2cm,top=3cm,bottom=2cm"

fontsize: 14pt
listings: true
indent: true
codeBlockCaptions: true
colorlinks: false

mainfont: Times New Roman
monofont: DejaVu Sans Mono
mathfont: XITS Math
lang: ru-RU
babel-lang: russian

figPrefix: 
  - рис.
  - рис.
tblPrefix: 
  - табл.
  - табл.
toc-title: СОДЕРЖАНИЕ
---

\pagebreak
# ВВЕДЕНИЕ {#sec:introduction -}

Одним из основных принципов объектно-ориентированного программирования является *наследование классов*, впервые введенное в языке Simula [@dahl1973simula]. Изначально наследование было только *одиночным*, то есть класс мог наследовать функциональность только одного класса, который назывался *суперклассом*, и иерархия классов представляла собой дерево или лес. Позже появились реализации языков с *множественным наследованием* (C++ [@ellis1990annotated], Eiffel [@meyer1992eiffel], Python [@van2003python] и др.), в которых стало возможным наследовать нескольких суперклассов и представлять иерархию в виде произвольного ациклического графа. Однако поддержка множественного наследования может значительно усложнить реализацию языка [@stroustrup1989multiple] по сравнению с одиночным наследованием.

В связи с этим многие современные языки программирования, такие как Java [@gosling2000java], C# [ @hejlsberg2003c], Objective-C [@kochan2011programming], Ruby [@flanagan2008ruby], отказались от полноценного множественного наследования в пользу *ограниченного множественного наследования* (также известного как *интерфейсное* или *гибридное* наследование). В такой модели наследования выделяются две категории типов: *классы*, которые наследуются одиночно, и *интерфейсы* (или *протоколы*), которые наследуются множественно. При этом класс может наследовать несколько интерфейсов, и тогда говорят, что класс *реализует* соответствующие интерфейсы. В отличие от полноценных классов, у интерфейсов не может быть состояния, то есть в них нельзя объявлять поля и от них нельзя создавать объекты. Такое разделение значительно упрощает реализацию языка, и в частности, реализацию полиморфных вызовов, которые разделяются на два вида, по аналогии с инструкциями в Java байткоде[^bytecode-invokes]: *виртуальные*, если формальный тип объекта вызова является классом, и *интерфейсные*, если формальный тип --- интерфейс.

Одиночное наследование классов позволяет эффективно реализовать виртуальные вызовы с помощью *таблиц виртуальных методов*, а ограничения интерфейсов позволяют использовать более эффективные и гибкие реализации интерфейсных вызовов, чем при полноценном множественном наследовании. Одна из таких реализаций основывается на использовании *таблиц интерфейсных методов* или *интерфейсных таблиц*. При таком подходе для каждого класса создается несколько таблиц, по одной на каждый суперинтерфейс, и возникает задача минимизации размера порождаемых таблиц. Существующие решения этой задачи рассматривают интерфейсные таблицы независимо от таблиц виртуальных методов, оставляя без внимания дублирование информации между ними. 

[^bytecode-invokes]: В Java байткоде [@lindholm2014java] виртуальные и интерфейсные вызовы осуществляются с помощью двух различных инструкций: `invokevirtual` и `invokeinterface`, соответственно. 

В данной работе предлагается новый алгоритм раскладки интерфейсных таблиц *внутри* таблицы виртуальных методов, минимизирующий суммарный размер таблиц без дополнительных издержек на вызовы. Измерения проводились на исследовательской виртуальной машине Excelsior RVM [@mikheev2002overview], состоящей из статического компилятора языка Java [@gosling2000java], и среды исполнения Java-программ [@lindholm2014java].

\pagebreak
# ПОСТАНОВКА ЗАДАЧИ {#sec:overview}

Зафиксируем некоторый язык программирования с ограниченным множественным наследованием, далее просто *язык*. Определим в этом языке множества классов $\mathbb{C}$ и интерфейсов $\mathbb{I}$ такие, что $\mathbb{C} \cap \mathbb{I} = \varnothing$, а также *иерархию типов* $\langle \mathbb{T}, \verb!<:! \rangle$ как множество $\mathbb{T} := \mathbb{C} \cup \mathbb{I}$ с *отношением подтипа* `<:`, которое задает частичный порядок на множестве $\mathbb{T}$. Множество всех предков некоторого типа $T$ будем обозначать $\mathbb{S}_T := \{S \in \mathbb{T}\ |\ T\ \verb!<:!\ S\} \setminus \{T\}.$ Тогда ограниченное множественное наследование определяется следующими условиями на отношение `<:`:

* $\forall C \in \mathbb{C}\ S \neq \varnothing \Rightarrow \exists! B \in S\ : \forall A \in S \quad B\ \verb!<:!\ A$, где $S = \mathbb{S}_C\ \cap\ \mathbb{C}$ --- задает одиночное наследование классов;
* $\forall I \in \mathbb{I} : \mathbb{S}_I \subset \mathbb{I}$ --- означает, что интерфейсы не могут наследовать классы.

*Методом* будем называть символическую ссылку на некоторую функцию, объявленную в классе или интерфейсе. На структуру или природу этой ссылки не накладывается никаких ограничений, например, это может быть строка, состоящая из имени и сигнатуры функции.

Для описания такой типовой системы будем использовать следующий набор сущностей, описанный в листинге [-@lst:type-system]. В листинге [-@lst:hierarchy] и на [@fig:hierarchy] приведен пример иерархии, описанной в этих терминах.

\noindent\Begin{minipage}{\linewidth}

```{#lst:type-system}
type Method // e.g. type Method := String
type Type := Class(super, interfaces, methods) |
             Interface(interfaces, methods)
```
: Определение структур метода, класса и интерфейса

\End{minipage}

\noindent\Begin{minipage}{\linewidth}

```{#lst:hierarchy}
K := Interface($\varnothing$,     { "c()" })
J := Interface($\varnothing$,     { "b()" })
I := Interface({J,K}, { "c()" })
B := Class(null, {J}, { "a()" })
C := Class(B,    {I}, { "b()" })
```
: Пример иерархии классов C, B и интерфейсов I, J, K

\End{minipage}

\begin{tikzfigure}{fig:hierarchy}{Графическое изображение иерархии в листинге~\ref{lst:hierarchy}}{}

    \matrix [row sep=1.5em, column sep=1.5em] {
    \node [class] (B) {B}; & \node [interface] (J) {J}; & \node [interface] (K) {K}; \\
    \node [class] (C) {C}; & \node [interface] (I) {I}; & \\
    };
    \graph [use existing nodes] {
        C -> {B -> J, I -> {J, K}}
    };

\end{tikzfigure}

Также определим следующие обозначения для произвольного типа T:

* $\mathbb{I}_T := \mathbb{S}_T \cap \mathbb{I}$ --- множество всех суперинтерфейсов типа T;
* $\mathbb{C}_T := \mathbb{S}_T \cap \mathbb{C}$ --- множество всех суперклассов типа T;
* $\mathbb{M}_T := \bigcup\limits_{S \in \mathbb{S}_T \cup \{T\}}S.methods$ --- множество методов, объявленных в типе T и в его предках.

## Полиморфные вызовы

*Реализацией метода* будем называть непосредственно функцию, на которую ссылается этот метод. Будем считать, что реализация представлена в виде адреса, указывающего на скомпилированное тело соответствующей функции, либо на некую процедуру, которая исполняет эту функцию.

Метод можно *вызвать* от переменной формального типа, в котором этот метод объявлен. Если у метода существует несколько реализаций и статически не известно, какая из них должна быть вызвана, то такой метод называется *полиморфным*. В таком случае необходимо произвести *позднее связывание* (англ. *late binding*) метода и его реализации во время исполнения. Вызываемая реализация определяется динамически в зависимости от типа объекта, который пришел в точку вызова, в соответствии с семантикой языка, с помощью некоторой процедуры
```
		resolve: (Class, Method) -> Addr,
```
\noindent которая по данному классу и методу возвращает искомую реализацию.

\noindent\Begin{minipage}{\linewidth}

```{#lst:naive-call}
addr := resolve(x.getClass(), "a()")
call addr
```
: Наивная реализация полиморфного вызова `x.a()`

\End{minipage}

Далее будем считать, что все вызовы являются полиморфными и разделяются на два вида, в зависимости от формального типа объекта вызова: *виртуальные*, если формальный тип является классом, и *интерфейсные*, если формальный тип --- интерфейс.

## Таблица виртуальных методов

Классический способ эффективной реализации полиморфных вызовов заключается в создании для каждого класса специальной структуры *таблицы виртуальных методов* (англ. *Virtual Method Table, VMT, vtable*) --- подход, впервые предложенный в языке Simula [@dahl1973simula]. Изначально таблица виртуальных методов разрабатывалась только для случая одиночного наследования, но в последствии была адаптирована и для множественного наследования при реализации языка C++ [@ellis1990annotated], хотя это потребовало значительного усложнения структуры и дополнительных издержек на вызовы [@driesen1996direct]. Тем не менее, благодаря своей высокой эффективности в случае одиночного наследования, таблицы виртуальных методов до сих пор успешно применяются для реализации виртуальных вызовов во многих современных языках с ограниченным множественным наследованием [@alpern2001efficient; @hejlsberg2003c; @kochan2011programming].

### Структура

Таблица виртуальных методов для некоторого класса C представляет собой массив $vmt_C$ реализаций методов класса C, доступный напрямую из любого объекта этого класса. Каждому методу m класса C назначается *виртуальный номер* $vnum_C(m)$, соответствующий уникальному (в пределах класса) индексу в $vmt_C$, по которому доступна реализация этого метода. Таким образом виртуальный вызов генерируется в обычный косвенный вызов по статически известному индексу в таблице виртуальных методов, как показано в листинге [-@lst:virtual-call].

\noindent\Begin{minipage}{\linewidth}

```{#lst:virtual-call}
// vnum := vnum$\textsubscript{C}$("a()") -- compile-time constant
call x.vmt[vnum] 
```
: Реализация виртуального вызова `x.a()`

\End{minipage}

Заметим, что такой косвенный вызов будет корректным, только если выполняется условие [-@eq:vmt-correctness]. В противном случае может вызваться некорректная реализация метода, находящаяся в ячейке с его виртуальным номером в таблице одного из наследников формального класса. 
$$
\begin{aligned}
\forall C,B \in \mathbb{C}\ \forall m \in \mathbb{M}_B &: \\
C\ \verb!<:!\ B\ &\Rightarrow vmt_C[vnum_B(m)] = resolve(C, m) 
\end{aligned} 
$$ {#eq:vmt-correctness} 

Обычно накладывают более сильное условие [-@eq:vmt-vnum-eq], требующее, чтобы наследники класса наследовали его виртуальные номера, что также можно переформулировать как: таблица виртуальных методов наследника должна *расширять* таблицу суперкласса. На [@fig:vmt-example] приведен пример такого расширения таблицы для класса C из иерархии приведенной в листинге [-@lst:hierarchy].
$$
\begin{aligned}
\forall C,B \in \mathbb{C}\ \forall m \in \mathbb{M}_B &: \\
C\ \verb!<:!\ B\ &\Rightarrow vnum_B(m) = vnum_C(m) 
\end{aligned} 
$$ {#eq:vmt-vnum-eq}

\begin{tikzfigure}{fig:vmt-example}{Таблица виртуальных методов для класса C}{}

    \begin{struct}{object}
        \header             {object header} {Object}
        \field [dotted]  {} {object vmt}    {\&VMT\textsubscript{C}}
        \field           {} {object rest}   {...}
    \end{struct}


    \begin{struct}[right={2*\structnodewidth} of object]{vmt}
        \header                {vmt header} {VMT\textsubscript{C}}
        \field [dotted]  {[0]} {vmt 0}      {\&B::a()}
        \field [dashed]  {[1]} {vmt 1}      {\&C::b()}
        \field           {[2]} {vmt 2}      {\&I::c()}
    \end{struct}

    \connect{object vmt}{vmt header}{}

\end{tikzfigure}

### Раскладка

*Раскладкой* таблицы виртуальных методов для класса C будем называть пару $(size_C, vnum_C)$, состоящую из размера этой таблицы и отображения виртуальных номеров класса C. Если раскладка удовлетворяет условию [-@eq:vmt-vnum-eq], то по ней можно построить корректную таблицу виртуальных методов, как показано в листинге [-@lst:vmt-building].

\noindent\Begin{minipage}{\linewidth}

```{#lst:vmt-building .numberLines startFrom=1}
vmt$\textsubscript{C}$ := new Array[Addr](size$\textsubscript{C}$)
for (m $\in$ $\(\mathbb{M}\)\textsubscript{C}$) {
  vmt$\textsubscript{C}$[vnum$\textsubscript{C}$(m)] := resolve(C, m)
}
```
: Построение VMT по раскладке для класса C

\End{minipage}

В листинге [-@lst:vmt-base-layout] приведен алгоритм построения *базовой раскладки* таблицы виртуальных методов для класса C, которая удовлетворяет условию [-@eq:vmt-vnum-eq]. 

\noindent\Begin{minipage}{\linewidth}

```{#lst:vmt-base-layout .numberLines startFrom=1}
def buildVMTLayout(C) = {
  size$\textsubscript{C}$ := 0
  vnum$\textsubscript{C}$ := $\varnothing$
    
  // 1. Наследование$\ $раскладки$\ $суперкласса
  B := C.super
  if (B $\neq$ null) {
    size$\textsubscript{C}$ := size$\textsubscript{B}$
    vnum$\textsubscript{C}$ := vnum$\textsubscript{B}$
  }
  
  // 2. Добавление$\ $новых$\ $методов$\ $класса C
  for (m $\in$ $\(\mathbb{M}\)\textsubscript{C}$ if m $\notin$ dom(vnum$\textsubscript{C}$)) {
    vnum$\textsubscript{C}$(m) := size$\textsubscript{C}$
    size$\textsubscript{C}$ := size$\textsubscript{C}$ + 1
  }
}
```
: Построение базовой раскладки для класса C

\End{minipage}

## Таблицы интерфейсных методов

Для реализации интерфейсных вызовов было предложено множество различных подходов, которые можно разделить на две категории: *динамические*, в которых поиск реализации производится явно в момент вызова с применением различных техник кэширования результатов, и *статические*, в которых результаты поиска вычисляются заранее до исполнения кода. Преимуществом статических подходов является константное время вызова, в то время как у динамических подходов вызовы в среднем происходят быстрее, однако в худшем случае динамический подход может оказаться даже медленнее наивной реализации. Недостатком же статических подходов является огромный размер порождаемых данных по сравнению с динамическими. Один из наиболее успешных статических подходов заключается в создании для каждой пары класса и его суперинтерфейса, *таблицы интерфейсных методов* (англ. *Interface Method Table, IMT, itable*). <!-- можно что-нибудь дописать сюда. -->

### Структура

Таблица интерфейсных методов (или *интерфейсная таблица*) для некоторого класса C и его суперинтерфейса I представляет собой массив $imt_{I,C}$ реализаций методов интерфейса I в классе C. Аналогично виртуальному случаю определяется уникальный (в пределах интерфейса) *виртуальный номер* $vnum_I(m)$ метода m в интерфейсе I, тем самым фиксируя индекс реализации в таблицах для всех классов, наследующих этот интерфейс. Таким образом, для каждой пары класс-интерфейс создается таблица, *поиск* которой делается в момент вызова, а затем делается косвенный вызов, аналогично виртуальному случаю, как показано в листинге [-@lst:interface-call]. В простейшей реализации, используется линейный поиск по всем суперинтерфейсам класса, как показано на [@fig:imt-example].

\noindent\Begin{minipage}{\linewidth}

```{#lst:interface-call}
// vnum := vnum$\textsubscript{I}$("a()") -- compile-time constant
imt := x.imts.find(&I)
call imt[vnum] 
```
: Реализация интерфейсного вызова `x.a()`

\End{minipage}

\begin{tikzfigure}{fig:imt-example}{Таблицы виртуальных и интерфейсных методов класса C}{}

    \begin{struct}{object}
        \header             {object header} {Object}
        \field [dotted]  {} {object vmt}    {\&VMT\textsubscript{C}}
        \field [dotted]  {} {object imts}   {\&IMTs}
        \field           {} {object rest}   {...}
    \end{struct}


    \begin{struct}[right={2*\structnodewidth} of object]{vmt}
        \header                {vmt header} {VMT\textsubscript{C}}
        \field [dotted]  {[0]} {vmt 0}      {\&B::a()}
        \field [dashed]  {[1]} {vmt 1}      {\&C::b()}
        \field           {[2]} {vmt 2}      {\&I::c()}
    \end{struct}

    \connect{object vmt}{vmt header}{}


    \begin{struct}[below=\structnodeheight of object rest]{imts}
        \header              {imts header} {IMTs}
        \field [dotted]  {I} {imts I}      {\&IMT\textsubscript{I,C}}
        \field [dotted]  {J} {imts J}      {\&IMT\textsubscript{J,C}}
        \field           {K} {imts K}      {\&IMT\textsubscript{K,C}}
    \end{struct}

    \draw [->] (object imts.west) 
            -| ($ (imts header.west) - (1,0) $)
            -- (imts header.west);



    \begin{struct}[below=\structnodeheight of vmt 2]{imt}
        \field [dotted]  {[0]} {imt I 0}    {\&C::b()}
        \field [draw]    {[1]} {imt I 1}    {\&I::c()}
        \field [draw]    {[0]} {imt J 0}    {\&C::b()}
        \field           {[0]} {imt K 0}    {\&I::c()}
    \end{struct}

    \imtR{imt I 0}{imt I 1}{IMT\textsubscript{I,C}}
    \imtR{imt J 0}{imt J 0}{IMT\textsubscript{J,C}}
    \imtR{imt K 0}{imt K 0}{IMT\textsubscript{K,C}}

    \begin{scope}[on background layer]
        \connect{imts I.east}{imt I 0.west}{}
        \connect{imts J.east}{imt J 0.west}{}
        \connect{imts K.east}{imt K 0.west}{}
    \end{scope}

\end{tikzfigure}

В отличие от таблицы виртуальных методов, корректность которой обеспечивалась за счет наследования виртуальных номеров, условие [-@eq:imt-correctness] выполняется автоматически за счет того, что виртуальные номера интерфейса не зависят от конкретного класса, реализующего данный интерфейс.

$$ 
\begin{aligned}
\forall C \in \mathbb{C}\ \forall I \in \mathbb{I}\ \forall m \in \mathbb{M}_C\ &: \\
C\ \verb!<:!\ I\ &\Rightarrow imt_{I,C}[vnum_I(m)] = resolve(C, m)
\end{aligned}
$$ {#eq:imt-correctness} 

### Раскладка

*Раскладкой* таблицы интерфейсных методов для интерфейса I будем называть пару $(size_I, vnum_I)$, состоящую из размера этой таблицы и отображения виртуальных номеров интерфейса I. Построение $imt_{I,C}$ для некоторого класса C, который реализует I представлено в листинге [-@lst:imt-building].

\noindent\Begin{minipage}{\linewidth}

```{#lst:imt-building .numberLines startFrom=1}
imt$\textsubscript{I,C}$ := new Array[Addr](size$\textsubscript{I}$)
for (m $\in$ $\(\mathbb{M}\)$$\textsubscript{I}$) {
  imt$\textsubscript{I,C}$[vnum$\textsubscript{I}$(m)] := resolve(C, m)
}
```
: Построение IMT по раскладке для интерфейса I в классе C 

\End{minipage}

В листинге [-@lst:imt-base-layout] приведен алгоритм построения *базовой раскладки* таблицы интерфейсных методов для интерфейса I.

\noindent\Begin{minipage}{\linewidth}

```{#lst:imt-base-layout .numberLines startFrom=1}
def buildIMTLayout(I) = {
  size$\textsubscript{I}$ := 0
  vnum$\textsubscript{I}$ := $\varnothing$
  for (m $\in$ $\(\mathbb{M}\)\textsubscript{I}$) {
    vnum$\textsubscript{I}$(m) := size$\textsubscript{I}$
    size$\textsubscript{I}$ := size$\textsubscript{I}$ + 1
  }
}
```
: Построение базовой раскладки для интерфейса I

\End{minipage}


### Существующие модификации {#sec:previous-work}

У базовой реализации таблиц интерфейсных методов можно выделить два основных недостатка:

1. Линейный поиск таблицы перед каждым вызовом;
2. Большой суммарный размер таблиц.

В связи с этим, при реализации языков обычно не используют таблицы интерфейсных методов в их базовом виде, а разрабатывают собственные модификации, адресующие один или оба недостатка.

Так, например, в CACAO JVM [@krall1997cacao] вместо линейного поиска используется поклассовая таблица соответствия интерфейсов и интерфейсных таблиц, проиндексированная в соответствии с глобальной нумерацией всех интерфейсов в программе. Такой подход позволяет реализовать интерфейсные вызовы почти так же эффективно как виртуальные, только с одной дополнительной косвенностью для получения интерфейсной таблицы. Однако на практике, большинство классов реализуют лишь несколько интерфейсов, и таблицы соответствия получаются большие и практически пустые. Из-за этого еще больше увеличивает потребление памяти, в дополнение к размерам самих таблиц интерфейсных методов.

В Marmot [@fitzgerald2000marmot], наоборот, поиск остается линейным, но используется модифицированная раскладка интерфейсных таблиц, в которой таблица интерфейса-наследника содержит все таблицы своих суперинтерфейсов. Благодаря такой вложенности, можно создавать только наибольшие по включению таблицы, что должно уменьшать суммарный размер таблиц. Однако существуют иерархии, на которых такое агрессивное наследование таблиц приводит не к уменьшению, а наоборот к увеличению суммарного размера таблиц.

В Jikes RVM [@alpern2001efficient] для интерфейсных вызовов применяется альтернативный подход, позволяющий не только избавиться от поиска таблиц, но и уменьшить их суммарный размер. Для каждого класса создается одна таблица фиксированного размера, при этом нескольким методам может соответствовать один виртуальный номер в этой таблице. В таком случае ячейка с этим номером содержит *процедуру разрешения конфликтов* (англ. *conflict resolution stub*), которая определяет с помощью неявного аргумента, какую из реализаций необходимо позвать. Наличие только одной таблицы позволяет превратить интерфейсный вызов в обычный косвенный, вызывающий нужную реализацию напрямую по ячейке в таблице, либо через дополнительную процедуру при наличии конфликтов. Эффективность такого подхода напрямую зависит от распределения виртуальных номеров, и в частности, от количества конфликтов. В Jikes RVM виртуальные номера назначаются в порядке загрузки классов, равномерно по фиксированному множеству индексов интерфейсной таблицы. Для такой схемы на практике можно встретить иерархии, на которых, аналогично подходу Marmot, суммарный размер данных интерфейсных таблиц (включая процедуры разрешения конфликтов) значительно превышает размер таблиц даже базовой реализации.

## Задача минимизации суммарного размера таблиц

Одна из главных задач, которые приходится решать при реализации интерфейсных вызовов с помощью таблиц интерфейсных методов --- это задача минимизации суммарного размера порождаемых таблиц. Поскольку для каждого класса необходимо создавать по таблице на каждый интерфейс, который он реализует, неизбежно возникает дублирование информации, то между самими таблицами интерфейсных методов, так и между таблицами виртуальных и интерфейсных методов одного класса.

Существующие решения [@fitzgerald2000marmot; @alpern2001efficient] рассматривают интерфейсные таблицы совершенно независимо от таблиц виртуальных методов, из-за чего могут уменьшать дублирование только между самими таблицами интерфейсных методов. Также, как будет продемонстрировано в главе [-@sec:results], существуют иерархии, на которых эти подходы порождают таблицы суммарно большего размера по сравнению с базовой реализацией.

Далее предлагается новый подход к реализации таблиц, использующий совмещенную раскладку таблиц виртуальных и интерфейсных методов, который позволяет сильнее ужимать размер таблиц. Более того, будет доказано, что использование такой раскладки дает результаты не хуже, чем базовая реализация таблиц.

\pagebreak
# СОВМЕЩЕННАЯ РАСКЛАДКА ТАБЛИЦ {#sec:layout}

Главная идея предлагаемого подхода заключается в том, чтобы расположить таблицы интерфейсных методов внутри таблицы виртуальных методов. Такое совмещение позволяет адресовать интерфейсные таблицы не по адресам в памяти, а по индексу внутри таблицы виртуальных методов, с которого таблица начинается. Эти индексы будем называть *интерфейсными номерами* и обозначать $inum_C(I)$ для каждого класса C и его суперинтерфейса I.

Введение интерфейсных номеров принципиально не меняет реализацию поиска интерфейсной таблицы, однако позволяет хранить меньше данных (вместо целого адреса таблицы хранить только индекс), и делать более эффективный косвенный вызов на некоторых архитектурах, как показано в листинге [-@lst:interface-call-inum]. 

\noindent\Begin{minipage}{\linewidth}

```{#lst:interface-call-inum}
// vnum := vnum$\textsubscript{I}$("a()") -- compile-time constant
inum := x.imts.find(&I)
call x.vmt[inum + vnum] 
```
: Реализация интерфейсного вызова `x.a()` с использованием интерфейсных номеров

\End{minipage}

## Базовая раскладка

Заметим, что применение интерфейсных номеров не является специфичным для предлагаемого подхода. Например, такие же интерфейсные номера можно использовать в базовой реализации, если расположить все IMT суперинтерфейсов после VMT соответствующего класса, как показано на [@fig:imt-inum-example] и [-@fig:base-layout].

\begin{tikzfigure}{fig:imt-inum-example}{Таблицы для класса C после введения интерфейсных номеров}{}

    \begin{struct}{object}
        \header             {object header} {Object}
        \field [dotted]  {} {object vmt}    {\&VMT\textsubscript{C}}
        \field [dotted]  {} {object imts}   {\&IMTs}
        \field           {} {object rest}   {...}
    \end{struct}


    \begin{struct}[right={2*\structnodewidth} of object]{vmt}
        \header                    {vmt header} {VMT\textsubscript{C}}
        \field [dotted]      {[0]} {vmt 0}      {\&B::a()}
        \field [dashed]      {[1]} {vmt 1}      {\&C::b()}
        \field [draw,thick]  {[2]} {vmt 2}      {\&I::c()}
        \field [dotted]      {[3]} {imt I 0}    {\&C::b()}
        \field [draw]        {[4]} {imt I 1}    {\&I::c()}
        \field [draw]        {[5]} {imt J 0}    {\&C::b()}
        \field               {[6]} {imt K 0}    {\&I::c()}
    \end{struct}

    \connect{object vmt}{vmt header}{}


    \begin{struct}[below=\structnodeheight of object rest]{imts}
        \header              {imts header} {IMTs}
        \field [dotted]  {I} {imts I}      {inum\textsubscript{I,C}}
        \field [dotted]  {J} {imts J}      {inum\textsubscript{J,C}}
        \field           {K} {imts K}      {inum\textsubscript{K,C}}
    \end{struct}

    \draw [->] (object imts.west) 
            -| ($ (imts header.west) - (1,0) $)
            -- (imts header.west);


    \imtR{imt I 0}{imt I 1}{IMT\textsubscript{I,C}}
    \imtR{imt J 0}{imt J 0}{IMT\textsubscript{J,C}}
    \imtR{imt K 0}{imt K 0}{IMT\textsubscript{K,C}}

    \begin{scope}[on background layer]
        \connect[0.25]{imts I.east}{imt I 0.west}{dashed}
        \connect[0.40]{imts J.east}{imt J 0.west}{dashed}
        \connect[0.55]{imts K.east}{imt K 0.west}{dashed}
    \end{scope}

\end{tikzfigure}

\begin{tikzfigure}{fig:base-layout}{Базовая раскладка таблиц из иерархии, изображенной на рис.~\ref{fig:hierarchy}}{}

    \begin{struct}{vmt B}
        \header                 {vmt B header} {VMT\textsubscript{B}}
        \field [dotted]      {} {vmt B 0}      {\&B::a()}
        \field [draw,thick]  {} {vmt B 1}      {\&J::b()}
        \field               {} {imt B J 0}    {\&J::b()}
    \end{struct}

    \imtL{imt B J 0}{imt B J 0}{IMT\textsubscript{J,B}}


    \begin{struct}[right={2*\structnodewidth} of vmt B]{vmt C}
        \header                 {vmt C header} {VMT\textsubscript{C}}
        \field [dotted]      {} {vmt C 0}      {\&B::a()}
        \field [dashed]      {} {vmt C 1}      {\&C::b()}
        \field [draw,thick]  {} {vmt C 2}      {\&I::c()}
        \field [dotted]      {} {imt C I 0}    {\&C::b()}
        \field [draw]        {} {imt C I 1}    {\&I::c()}
        \field [draw]        {} {imt C J 0}    {\&C::b()}
        \field               {} {imt C K 0}    {\&I::c()}
    \end{struct}


    \imtR{imt C I 0}{imt C I 1}{IMT\textsubscript{I,C}}
    \imtR{imt C J 0}{imt C J 0}{IMT\textsubscript{J,C}}
    \imtR{imt C K 0}{imt C K 0}{IMT\textsubscript{K,C}}


    \begin{scope}[on background layer]
        \draw [dashed] (vmt B 0.north east) -- (vmt C 0.north west);
        \draw [dashed] (vmt B 1.south east) -- (vmt C 1.south west);
    \end{scope}

\end{tikzfigure}

<!-- ### Оценка размера -->

Для дальнейших оценок размеров различных раскладок, нам понадобится получить исходную оценку на суммарный размер таблиц базовой раскладки.

Размер таблицы некоторого типа T, получаемой при использовании какой-либо раскладки, зависит исключительно от выбранной раскладки, а именно от размера $size_T$. Поэтому далее будем отождествлять размер раскладки с размером таблицы полученной по этой раскладке. 

\refstepcounter{stmt}\label{stmt:base-layout-size}
\textbf{Утверждение \thestmt.\ } Для базовой раскладки класса или интерфейса T выполняется равенство:
$$
size_T = |\mathbb{M}_T|.
$$ {#eq:base-layout-size}

*Доказательство.* В случае, если T является интерфейсом, равенство [-@eq:base-layout-size] выполняется по построению (см. листинг [-@lst:imt-base-layout]). Для случая, когда T является классом, по построению (см. листинг [-@lst:vmt-base-layout]) получаются следующие рекуррентные соотношения:
$$
size_T \underset{B=T.super}{=} \begin{cases}
  |\mathbb{M}_T|, & B = null \\
  size_B + |\mathbb{M}_T \setminus \mathbb{M}_B|, & \text{\it иначе}
  \end{cases},
$$
\noindent из которых индукцией по глубине наследования выводится искомое равенство. $\square$

Таким образом, из доказанного утверждения вытекает следующая оценка на суммарный размер таблиц базовой раскладки:
$$
\begin{aligned}
Size_{Base} = \sum\limits_{C \in \mathbb{C}} (size_C + \sum\limits_{I \in \mathbb{I}_C} size_I) \overset{\text{Утв. \ref{stmt:base-layout-size}}}{=} \sum\limits_{C \in \mathbb{C}} (|\mathbb{M}_C| + \sum\limits_{I \in \mathbb{I}_C} |\mathbb{M}_I|).
\end{aligned}
$$ {#eq:base-layout-total-size}

## Совмещение VMT и IMT

*Совмещенной раскладкой* таблицы виртуальных методов класса C будем называть тройку $(size_C, vnum_C, inum_C)$, состоящую из размера таблицы, отображения виртуальных номеров методов и отображения интерфейсных номеров суперинтерфейсов класса C. При выполнении условий [-@eq:vmt-vnum-eq] и [-@eq:vmt-vnum-inum], можно построить корректную таблицу виртуальных методов, как показано в листинге [-@lst:vmt-imt-building].
$$ 
\begin{aligned}
\forall C \in \mathbb{C}\ \forall m_C \in \mathbb{M}_C\ \forall I \in \mathbb{I}_C\ \forall m_I \in \mathbb{M}_I &: \\
vnum_C(m_C) = inum_C(I)\ +\ &vnum_I(m_I) \Rightarrow m_C = m_I 
\end{aligned} 
$$ {#eq:vmt-vnum-inum}

\noindent\Begin{minipage}{\linewidth}

```{#lst:vmt-imt-building .numberLines startFrom=1}
vmt$\textsubscript{C}$ := new Array[Addr](size$\textsubscript{C}$)
for (I $\in$ $\(\mathbb{I}\)\textsubscript{C}$, m $\in$ $\(\mathbb{M}\)\textsubscript{I}$) {
  vmt$\textsubscript{C}$[inum$\textsubscript{C}$(I) + vnum$\textsubscript{I}$(m)] := resolve(C, m)
}
for (m $\in$ $\(\mathbb{M}\)\textsubscript{C}$) {
  vmt$\textsubscript{C}$[vnum$\textsubscript{C}$(m)] := resolve(C, m)
}
```
: Построение VMT по совмещенной раскладке для класса C

\End{minipage}

Условие [-@eq:vmt-vnum-inum] необходимо для того, чтобы при заполнении ячеек методов класса (в строчках 5-7) не возникало конфликтов с уже записанными реализациями интерфейсных таблиц. Именно благодаря такому условию, становится возможным уменьшение размера VMT, за счет назначения методам класса виртуальных номеров, указывающих внутрь интерфейсных таблиц.

Построение совмещенной раскладки таблицы виртуальных методов, изображенной на [@fig:vmt-imt-layout], представлено в листингах [-@lst:vmt-imt-layout] и [-@lst:vmt-imt-add-imt] и происходит в три этапа: 

1. Наследование раскладки суперкласса, включая интерфейсные номера его суперинтерфейсов, что позволяет удовлетворить условию [-@eq:vmt-vnum-eq], а также позволяет явно не добавлять в раскладку данного класса интерфейсные таблицы суперкласса;
2. Добавление *собственных* методов класса, которые не объявлены в суперклассах или суперинтерфейсах, и назначение им виртуальных номеров. В отличие от базовой раскладки из листинга [-@lst:vmt-base-layout], методам, унаследованным от интерфейсов, виртуальные номера будут назначены внутри интерфейсных таблиц, тем самым уменьшая дублирование между ними;
3. Добавление IMT *собственных* суперинтерфейсов, которым еще не назначен интерфейсный номер с помощью процедуры $addIMT_C$, представленной в листинге [-@lst:vmt-imt-add-imt]. При этом происходит назначение виртуальных номеров методам из этих таблиц, у которых виртуального номера еще нет, явно гарантируя выполнение условия [-@eq:vmt-vnum-inum].

\begin{tikzfigure}{fig:vmt-imt-layout}{Совмещенная раскладка таблиц из иерархии, изображенной на рис.~\ref{fig:hierarchy}}{}

    \begin{struct}{vmt B}
        \header                 {vmt B header} {VMT\textsubscript{B}}
        \field [dashed]      {} {vmt B 0}      {\&B::a()}
        \field               {} {imt B J 0}    {\&J::b()}
    \end{struct}

    \imtL{imt B J 0}{imt B J 0}{IMT\textsubscript{J,B}}


    \begin{struct}[right={2*\structnodewidth} of vmt B]{vmt C}
        \header                 {vmt C header} {VMT\textsubscript{C}}
        \field [dashed]      {} {vmt C 0}      {\&B::a()}
        \field [dashed]      {} {imt C J 0}    {\&C::b()}
        \field [dashed]      {} {imt C K 0}    {\&I::c()}
        \field [dotted]      {} {imt C I 0}    {\&C::b()}
        \field               {} {imt C I 1}    {\&I::c()}
    \end{struct}


    \imtR{imt C I 0}{imt C I 1}{IMT\textsubscript{I,C}}
    \imtL{imt C J 0}{imt C J 0}{IMT\textsubscript{J,C}}
    \imtR{imt C K 0}{imt C K 0}{IMT\textsubscript{K,C}}


    \begin{scope}[on background layer]
        \draw [dashed] (vmt B 0.north east) -- (vmt C 0.north west);
        \draw [dashed] (imt B J 0.south east) -- (imt C J 0.south west);
    \end{scope}

\end{tikzfigure}

\noindent\Begin{minipage}{\linewidth}

```{#lst:vmt-imt-layout .numberLines startFrom=1}
def buildVMTLayout(C) = {
  size$\textsubscript{C}$ := 0
  vnum$\textsubscript{C}$ := $\varnothing$
  inum$\textsubscript{C}$ := $\varnothing$
  
  // 1. Наследование$\ $раскладки$\ $суперкласса
  B := C.super
  if (B $\neq$ null) {
    size$\textsubscript{C}$ := size$\textsubscript{B}$
    vnum$\textsubscript{C}$ := vnum$\textsubscript{B}$
    inum$\textsubscript{C}$ := inum$\textsubscript{B}$
  }
  
  // 2. Добавление$\ $собственных$\ $методов$\ $класса C
  for (m $\in$ $\(\mathbb{M}\)\textsubscript{C}$ if $\forall$S $\in$ $\(\mathbb{S}\)\textsubscript{C}$ : m $\notin$ $\(\mathbb{M}\)\textsubscript{S}$) {
    vnum$\textsubscript{C}$(m) := size$\textsubscript{C}$
    size$\textsubscript{C}$ := size$\textsubscript{C}$ + 1
  }
  
  // 3. Добавление IMT собственных$\ $интерфейсов
  for (I $\in$ $\(\mathbb{I}\)\textsubscript{C}$ if I $\notin$ dom(inum$\textsubscript{C}$)) {
    addIMT$\textsubscript{C}$(size$\textsubscript{C}$, I)
    size$\textsubscript{C}$ := size$\textsubscript{C}$ + size$\textsubscript{I}$
  }
}
```
: Построение совмещенной раскладки для класса C

\End{minipage}

\noindent\Begin{minipage}{\linewidth}

```{#lst:vmt-imt-add-imt .numberLines startFrom=1}
def addIMT$\textsubscript{C}$(pos, I) = {
  inum$\textsubscript{C}$(I) := pos
  
  // Назначение$\ $виртуальных$\ $номеров$\ $внутри IMT
  for (m $\in$ $\(\mathbb{M}\)\textsubscript{I}$ if m $\notin$ dom(vnum$\textsubscript{C}$)) {
    vnum$\textsubscript{C}$(m) := inum$\textsubscript{C}$(I) + vnum$\textsubscript{I}$(m)
  }
}
```
: Процедура добавления раскладки интерфейса I в раскладку для класса C

\End{minipage}

<!-- ### Оценка размера -->

Заметим, что так как раскладка интерфейсных таблиц осталась базовой, то для нее верно утверждение \ref{stmt:base-layout-size} и выполняется равенство [-@eq:base-layout-size].

\refstepcounter{stmt}\label{stmt:vmt-imt-layout-size}
\textbf{Утверждение \thestmt.\ } Для совмещенной раскладки VMT класса C выполняется:
$$
size_C \leq |\mathbb{M}_C| + \sum\limits_{I \in \mathbb{I}_C}|\mathbb{M}_I|
$$ {#eq:vmt-imt-layout-size}

*Доказательство.* По построению раскладки (см. листинг [-@lst:vmt-imt-layout]) получаются следующие рекуррентные соотношения:
$$
\begin{aligned}
size_C = |M| + \begin{cases}
  \sum\limits_{I \in \mathbb{I}_C}|size_I|, & B = null \\
  size_B + \sum\limits_{I \in \mathbb{I}_C \setminus \mathbb{I}_B}|size_I|, & \text{\it иначе}
  \end{cases},
\end{aligned}
$$
\noindent где $B = C.super$ и $M = \mathbb{M}_C \setminus \bigcup\limits_{S \in \mathbb{S}_C}\mathbb{M}_S$.

Докажем утверждение \ref{stmt:vmt-imt-layout-size} индукцией по глубине наследования классов:

<!-- Bug with \noindent* -->
\noindent {\it База индукции} *($B = null$):*
$$
\begin{aligned}
size_C = &|M| + \sum\limits_{I \in \mathbb{I}_C}|size_I| \overset{\text{Утв. \ref{stmt:base-layout-size}}}{=} \\
&|M| + \sum\limits_{I \in \mathbb{I}_C}|\mathbb{M}_I| \leq |\mathbb{M}_C| + \sum\limits_{I \in \mathbb{I}_C}|\mathbb{M}_I|.
\end{aligned}
$$
*Шаг индукции ($B \neq null$):*
$$
\begin{aligned}
size_C = |M| + size_B + \sum\limits_{I \in \mathbb{I}_C \setminus \mathbb{I}_B}|size_I| &\overset{\text{Утв. \ref{stmt:base-layout-size}}}{=} \\
|M| + size_B + \sum\limits_{I \in \mathbb{I}_C \setminus \mathbb{I}_B}|\mathbb{M}_I| &\overset{\text{По инд.}}{\leq} \\
|M| + |\mathbb{M}_B| + \sum\limits_{I \in \mathbb{I}_B}|\mathbb{M}_I| + \sum\limits_{I \in \mathbb{I}_C \setminus \mathbb{I}_B}|\mathbb{M}_I| &= \\
|M| + |\mathbb{M}_B| + \sum\limits_{I \in \mathbb{I}_C}|\mathbb{M}_I| &= \\
|M \cup \mathbb{M}_B| + \sum\limits_{I \in \mathbb{I}_C}|\mathbb{M}_I| &\leq \\
|\mathbb{M}_C| + \sum\limits_{I \in \mathbb{I}_C}|\mathbb{M}_I|&.\ \square
\end{aligned}
$$

## Совмещение IMT и IMT {#sec:imt-imt}

По аналогии с таблицей виртуальных методов, можно внести таблицы суперинтерфейсов в таблицу интерфейса-наследника, как это сделано в системе Marmot [@fitzgerald2000marmot], и определить *совмещенную раскладку* таблицы интерфейсных методов интерфейса I как тройку $(size_I, vnum_I, inum_I)$, состоящую из размера таблицы, отображения виртуальных номеров методов и отображения интерфейсных номеров суперинтерфейсов интерфейса I. Однако, как было замечено авторами Marmot, добавление всех таблиц суперинтерфейсов не всегда дает уменьшение размеров таблиц. Например, если у двух суперинтерфейсов совпадают некоторые методы, то при внесении их таблиц в таблицу интерфейса-наследника возникает дублирование информации, которого не было в базовой раскладке. Для того чтобы избежать такого дублирования, мы будем добавлять в раскладку IMT только непересекающиеся по методам таблицы суперинтерфейсов, как показано в листинге [-@lst:imt-imt-layout]. Построение совмещенной раскладки для IMT происходит в два этапа: 

1. Добавление наибольших по включению и непересекающихся по методам IMT суперинтерфейсов в порядке убывания их размеров, которым еще не назначен интерфейсный номер. При этом происходит назначение виртуальных номеров тем методам из этих таблиц, у которых виртуального номера еще нет. Добавление IMT осуществляется с помощью модифицированной процедуры $addIMT_T$ из листинга [-@lst:vmt-imt-add-imt], расширенной на все типы и представленной в листинге [-@lst:imt-imt-add-imt];
2. Добавление оставшихся методов и назначение им виртуальных номеров. В отличие от базовой раскладки из листинга [-@lst:imt-base-layout], методам, унаследованным от интерфейсов, виртуальные номера будут назначены внутри интерфейсных таблиц.

\noindent\Begin{minipage}{\linewidth}

```{#lst:imt-imt-layout .numberLines startFrom=1}
def buildIMTLayout(I) = {
  size$\textsubscript{I}$ := 0
  vnum$\textsubscript{I}$ := $\varnothing$
  inum$\textsubscript{I}$ := $\varnothing$
  
  // 1. Добавление$\ $наибольших$\ $по$\ $включению$\ $и$\ $непересекающихся$\ $по$\ $методам IMT в$\ $порядке$\ $убывания$\ $их$\ $размеров
  Max := {M $\in$ $\(\mathbb{I}\)\textsubscript{I}$ | $\forall$J $\in$ $\(\mathbb{I}\)\textsubscript{I}$ : M $\notin$ dom(inum$\textsubscript{J}$)}
  for (J $\in$ Max ordered by (-$size\textsubscript{J}$) if J $\notin$ dom(inum$\textsubscript{I}$)) {
    if ($\(\mathbb{M}\)\textsubscript{J}$ $\cap$ dom(vnum$\textsubscript{I}$) = $\varnothing$) {
      addIMT$\textsubscript{I}$(size$\textsubscript{I}$, J)
      size$\textsubscript{I}$ := size$\textsubscript{I}$ + size$\textsubscript{J}$
    }
  }
  
  // 2. Добавление$\ $оставшихся$\ $методов$\ $интерфейса I
  for (m $\in$ $\(\mathbb{M}\)\textsubscript{I}$ if m $\notin$ dom(vnum$\textsubscript{I}$)) {
    vnum$\textsubscript{I}$(m) := size$\textsubscript{I}$
    size$\textsubscript{I}$ := size$\textsubscript{I}$ + 1
  }
}
```
: Построение совмещенной раскладки для интерфейса I

\End{minipage}

\noindent\Begin{minipage}{\linewidth}

```{#lst:imt-imt-add-imt .numberLines startFrom=1}
def addIMT$\textsubscript{T}$(pos, I): Int = {
  inum$\textsubscript{T}$(I) := pos
  
  // Назначение$\ $виртуальных$\ $номеров$\ $внутри IMT
  for (m $\in$ $\(\mathbb{M}\)\textsubscript{I}$ if m $\notin$ dom(vnum$\textsubscript{T}$)) {
    vnum$\textsubscript{T}$(m) := inum$\textsubscript{T}$(I) + vnum$\textsubscript{I}$(m)
  }
  
  // Наследование$\ $интерфейсных$\ $номеров
  for (J $\in$ dom(inum$\textsubscript{I}$) if J $\notin$ dom(inum$\textsubscript{T}$)) {
    inum$\textsubscript{T}$(J) := inum$\textsubscript{T}$(I) + inum$\textsubscript{I}$(J)
  }
}
```
: Процедура добавления IMT интерфейса I в раскладку типа T

\End{minipage}

Пример таблиц, построенных по такой раскладке, изображен на [@fig:imt-imt-layout].

\begin{tikzfigure}{fig:imt-imt-layout}{Совмещенная раскладка таблиц из иерархии, изображенной на рис.~\ref{fig:hierarchy}}{}

    \begin{struct}{vmt B}
        \header                 {vmt B header} {VMT\textsubscript{B}}
        \field [dashed]      {} {vmt B 0}      {\&B::a()}
        \field               {} {imt B J 0}    {\&J::b()}
    \end{struct}

    \imtL{imt B J 0}{imt B J 0}{IMT\textsubscript{J,B}}


    \begin{struct}[right={2*\structnodewidth} of vmt B]{vmt C}
        \header                 {vmt C header} {VMT\textsubscript{C}}
        \field [dashed]      {} {vmt C 0}      {\&B::a()}
        \field [dashed]      {} {imt C J 0}    {\&C::b()}
        \field [dashed]      {} {imt C I 0}    {\&C::b()}
        \field               {} {imt C K 0}    {\&I::c()}
    \end{struct}


    \imtR{imt C I 0}{imt C K 0}{IMT\textsubscript{I,C}}
    \imtL{imt C I 0}{imt C I 0}{IMT\textsubscript{J,C}}
    \imtL{imt C J 0}{imt C J 0}{IMT\textsubscript{J,C}}
    \imtL{imt C K 0}{imt C K 0}{IMT\textsubscript{K,C}}


    \begin{scope}[on background layer]
        \draw [dashed] (vmt B 0.north east) -- (vmt C 0.north west);
        \draw [dashed] (imt B J 0.south east) -- (imt C J 0.south west);
    \end{scope}

\end{tikzfigure}

Таким образом, в $addIMT_T$ появляется наследование интерфейсных номеров и соответствующих подтаблиц от интерфейса, таблица которого добавляется. Заметим, что это наследование происходит как при построении IMT, так и при построении VMT, что позволяет уменьшить количество добавляемых таблиц и, как следствие, дублирование информации между ними. Для этого в совмещенной раскладке VMT из листинга [-@lst:vmt-imt-layout] также стоит добавлять только наибольшие по включению IMT (см. листинг [-@lst:vmt-imt-layout-2]).

\noindent\Begin{minipage}{\linewidth}

```{#lst:vmt-imt-layout-2 .numberLines startFrom=19}
// 3. Добавление$\ $наибольших$\ $по$\ $включению IMT
Max := {M $\in$ $\(\mathbb{I}\)\textsubscript{C}$ | $\forall$J $\in$ $\(\mathbb{I}\)\textsubscript{C}$ : M $\notin$ dom(inum$\textsubscript{J}$)}
for (I $\in$ Max if I $\notin$ dom(inum$\textsubscript{C}$)) {
  addIMT$\textsubscript{C}$(size$\textsubscript{C}$, I)
  size$\textsubscript{C}$ := size$\textsubscript{C}$ + size$\textsubscript{I}$
}
```
: Модификация процедуры `buildVMTLayout(C)`

\End{minipage}

<!-- ### Оценка размера -->

Заметим, что для полученной совмещенной раскладки таблиц выполняется следующее условие:
$$
\begin{aligned}
\forall I \in \mathbb{I}\ \forall S_1, S_2 \in dom(inum_I) : \mathbb{M}_{S_1} \cap \mathbb{M}_{S_2} = \varnothing.
\end{aligned}
$$ {#eq:imt-imt-inum}

\refstepcounter{stmt}\label{stmt:imt-imt-layout-size}
\textbf{Утверждение \thestmt.\ } Для совмещенной раскладки IMT интерфейса I выполняется:
$$
size_I = |\mathbb{M}_I|
$$ {#eq:imt-imt-layout-size}

*Доказательство.* По построению раскладки (см. листинг [-@lst:imt-imt-layout]) получаются следующие рекуррентные соотношения:
$$
\begin{aligned}
size_I = \sum\limits_{J \in D}|size_J| + |M|,
\end{aligned}
$$
\noindent где $D = dom(inum_I)$ и $M = \mathbb{M}_I \setminus \bigcup\limits_{J \in D}\mathbb{M}_J$.

Докажем утверждение \ref{stmt:imt-imt-layout-size} индукцией по количеству подтаблиц в IMT интерфейса I:

<!-- Bug with \noindent* -->
\noindent {\it База индукции} *($D = \varnothing$):*
$$
\begin{aligned}
size_I = |M| = |\mathbb{M}_I|.
\end{aligned}
$$
*Шаг индукции ($D \neq \varnothing$):*
$$
\begin{aligned}
size_C = \sum\limits_{J \in D}|size_J| + |M| &\overset{\text{По инд.}}{\leq} \\
\sum\limits_{J \in D}|\mathbb{M}_J| + |M| &\overset{\text{(\ref{eq:imt-imt-inum})}}{=} \\
|\bigcup\limits_{J \in D}\mathbb{M}_J| + |M| &= \\
|\mathbb{M}_I|&.\ \square
\end{aligned}
$$

**Следствие.** Утверждение \ref{stmt:vmt-imt-layout-size} выполняется после совмещения раскладок таблиц интерфейсных методов.

## Расширение последней IMT суперкласса {#sec:last-imt}

Если посмотреть на структуру полученных совмещенных раскладок таблиц виртуальных и интерфейсных методов, то можно заметить, что интерфейсные таблицы суперинтерфейсов собираются в конце VMT и в начале IMT. Такая структура позволяет делать дополнительное преобразование, изображенное на [@fig:combined-layout], позволяющее еще сильнее уменьшить размер таблиц, которое заключается в расширении последней интерфейсной таблицы суперкласса[^last-imt] с помощью интерфейсной таблицы наследника, как показано в листинге [-@lst:combined-layout]. 

[^last-imt]: В общем случае последних таблиц у VMT может быть несколько. Например, если класс наследует цепочку интерфейсов I `<:` J, и в интерфейсе I нет объявлений собственных методов, то последней можно считать как таблицу I, так и таблицу J. В таком случае следует перебрать все последние таблицы, начиная с наибольшей по включению (в данном примере начиная с I), и выбрать первую непустую, которую можно расширить.

\begin{tikzfigure}{fig:combined-layout}{Итоговая совмещенная раскладка таблиц из иерархии, изображенной на рис.~\ref{fig:hierarchy}}{}

    \begin{struct}{vmt B}
        \header                 {vmt B header} {VMT\textsubscript{B}}
        \field [dashed]      {} {vmt B 0}      {\&B::a()}
        \field               {} {imt B J 0}    {\&J::b()}
    \end{struct}

    \imtL{imt B J 0}{imt B J 0}{IMT\textsubscript{J,B}}


    \begin{struct}[right={2*\structnodewidth} of vmt B]{vmt C}
        \header                 {vmt C header} {VMT\textsubscript{C}}
        \field [dashed]      {} {vmt C 0}      {\&B::a()}
        \field [dashed]      {} {imt C J 0}    {\&C::b()}
        \field               {} {imt C K 0}    {\&I::c()}
    \end{struct}


    \imtR{imt C J 0}{imt C K 0}{IMT\textsubscript{I,C}}
    \imtL{imt C J 0}{imt C J 0}{IMT\textsubscript{J,C}}
    \imtL{imt C K 0}{imt C K 0}{IMT\textsubscript{K,C}}


    \begin{scope}[on background layer]
        \draw [dashed] (vmt B 0.north east) -- (vmt C 0.north west);
        \draw [dashed] (imt B J 0.south east) -- (imt C J 0.south west);
    \end{scope}

\end{tikzfigure}

\noindent\Begin{minipage}{\linewidth}

```{#lst:combined-layout .numberLines startFrom=1}
def buildVMTLayout(C) = {
  size$\textsubscript{C}$ := 0
  vnum$\textsubscript{C}$ := $\varnothing$
  inum$\textsubscript{C}$ := $\varnothing$

  // 0. Множество$\ $наибольших$\ $по$\ $включению$\ $суперинтерфейсов
  Max := {M $\in$ $\(\mathbb{I}\)\textsubscript{C}$ | $\forall$J $\in$ $\(\mathbb{I}\)\textsubscript{C}$ : M $\notin$ dom(inum$\textsubscript{J}$)}

  // 1. Наследование$\ $раскладки$\ $суперкласса
  B := C.super
  if (B $\neq$ null) {
    size$\textsubscript{C}$ := size$\textsubscript{B}$
    vnum$\textsubscript{C}$ := vnum$\textsubscript{B}$
    inum$\textsubscript{C}$ := inum$\textsubscript{B}$
    
    // 1.1. Расширение$\ $последней IMT суперкласса
    Last := {I $\in$ $\(\mathbb{I}\)\textsubscript{B}$ : size$\textsubscript{I}$ > 0 && inum$\textsubscript{B}$(I) + size$\textsubscript{I}$ = size$\textsubscript{B}$}
    for (I $\in$ Last ordered by $\(\langle\)$-size$\textsubscript{I}$, <:$\(\rangle\)$; 
         J $\in$ Max \ $\(\mathbb{I}\)\textsubscript{B}$ if inum$\textsubscript{J}$(I) = 0) {
      addIMT$\textsubscript{C}$(inum$\textsubscript{B}$(I), J)
      size$\textsubscript{C}$ := inum$\textsubscript{B}$(I) + size$\textsubscript{J}$
      break // Расширять$\ $можно$\ $только$\ $один$\ $раз
    }
  }
  
  // 2. Добавление$\ $собственных$\ $методов$\ $класса C
  for (m $\in$ $\(\mathbb{M}\)\textsubscript{C}$ if $\forall$S $\in$ $\(\mathbb{S}\)\textsubscript{C}$ : m $\notin$ $\(\mathbb{M}\)\textsubscript{S}$) {
    vnum$\textsubscript{C}$(m) := size$\textsubscript{C}$
    size$\textsubscript{C}$ := size$\textsubscript{C}$ + 1
  }
  
  // 3. Добавление$\ $наибольших$\ $по$\ $включению IMT в$\ $порядке$\ $возрастания$\ $их$\ $размеров
  for (I $\in$ Max ordered by size$\textsubscript{I}$ if I $\notin$ dom(inum$\textsubscript{C}$)) {
    addIMT$\textsubscript{C}$(size$\textsubscript{C}$, I)
    size$\textsubscript{C}$ := size$\textsubscript{C}$ + size$\textsubscript{I}$
  }
}
```
: Построение итоговой совмещенной раскладки для класса C

\End{minipage}

Для того чтобы повысить эффект от срабатывания представленного преобразования, применяется жадная эвристика, которая помещает наибольшую IMT последней в таблицу VMT так, чтобы в наследниках расширялась всегда наибольшая IMT. Реализована эта эвристика в виде сортировки IMT по возрастанию их размеров при добавлении в VMT.

<!-- ### Оценка размера -->

Заметим, что оценка на размер таблиц из утверждения \ref{stmt:vmt-imt-layout-size} остается верной, так как расширение последней IMT суперкласса может только уменьшить размер таблицы.

Таким образом, суммарный размер таблиц совмещенной раскладки не превосходит суммарного размера таблиц базовой раскладки:
$$
\begin{aligned}
Size_{Combined} = &\sum\limits_{C \in \mathbb{C}} size_C \overset{\text{Утв. \ref{stmt:vmt-imt-layout-size}}}{\leq} \\
&\sum\limits_{C \in \mathbb{C}} (|\mathbb{M}_C| + \sum\limits_{I \in \mathbb{I}_C} |\mathbb{M}_I|) \overset{\text{(\ref{eq:base-layout-total-size})}}{=} Size_{Base}.
\end{aligned}
$$ {#eq:combined-layout-total-size}

\pagebreak
# РЕЗУЛЬТАТЫ ИЗМЕРЕНИЙ {#sec:results}

Итоговая совмещенная раскладка была реализована в исследовательской виртуальной машине Excelsior RVM для языка Java (подробное описание реализации приведено в приложении). Изначально в виртуальной машине использовалась базовая раскладка (см. листинги [-@lst:vmt-base-layout] и [-@lst:imt-base-layout]). Тестирование проводилось на следующем наборе приложений:

* JET Runtime --- среда исполнения виртуальной машины Excelsior RVM, включающая основную часть стандартной библиотеки Java и JIT-компилятор, написанный на Java и Scala [@odersky2004overview];
* JRuby 9.1.8.0 --- реализация языка JRuby [@developers2008jruby], написанная на Ruby [@flanagan2008ruby] и Java;
* Elasticsearch 5.5.0 --- популярная поисковая система [@gormley2015elasticsearch], написанная на Java;
* Scalac 2.11.7 --- самокомпилирующийся компилятор языка Scala;
* Kotlinc --- самокомпилирующийся компилятор языка Kotlin [@samuel2017programming];
* Eclipse 4.6.1 --- интегрированная среда разработки Eclipse Neon, написанная на Java;
* IDEA 2017.2.1 --- интегрированная среда разработки Intellij IDEA Community Edition, написанная на Java.

Данные, представленные в результатах, получены при помощи статического компилятора Excelsior RVM для архитектуры x86_64 и агрегированы по всем классам приложения, без учета динамической загрузки классов. 

| Приложение          | Количество классов | Количество интерфейсов |
|:--------------------|-------------------:|-----------------------:|
| JET Runtime         | 7992               | 690                    |
| JRuby 9.1.8.0       | 8484               | 360                    |
| Elasticsearch 5.5.0 | 15033              | 1189                   |
| Scalac 2.11.7       | 18213              | 1649                   |
| Kotlinc             | 11563              | 1572                   |
| Eclipse 4.6.1       | 65286              | 8142                   |
| IDEA 2017.2.1       | 112941             | 12171                  |

: Размеры иерархий тестируемых приложений {#tbl:test-apps}

На [@fig:layout-stats] представлено суммарное уменьшение размера интерфейсных таблиц от каждого из преобразований рассмотренных в главе [-@sec:layout], относительно базовой раскладки таблиц. На всех тестируемых приложениях итоговая раскладка дает более чем трехкратное уменьшение: от 69% до 98% от исходного размера таблиц.

<div id="fig:layout-stats">
![](images/layout-stats-1.pdf){#fig:layout-stats-1}

![](images/layout-stats-2.pdf){#fig:layout-stats-2}

Суммарное уменьшение размера интерфейсных таблиц, kB

</div>

\clearpage
## Сравнение с существующими подходами {#sec:results-other}

Среди существующих реализаций таблиц интерфейсных методов, можно выделить две, которые продвинулись дальше других в решении задачи уменьшения размера порождаемых таблиц: реализации таблиц в исследовательских виртуальных машинах Jikes RVM [@alpern2001efficient] и Marmot [@fitzgerald2000marmot], которые описаны в главе [-@sec:previous-work]. Оба подхода были реализованы в Excelsior RVM[^jikes-impl] для проведения измерений, результаты которых представлены на [@fig:layout-compare] вместе с исходной реализацией Excelsior RVM и итоговой раскладкой таблиц. 

[^jikes-impl]: В оригинальном подходе Jikes RVM предлагалась таблица размером в 5 элементов, и назначение виртуальных номеров делалось в порядке загрузки классов. В реализации этого подхода в Excelsior RVM используется такой же размер таблицы, а виртуальные номера назначаются в порядке обработки классов статическим компилятором.

<div id="fig:layout-compare">
![](images/layout-compare-1.pdf){#fig:layout-compare-1}

![](images/layout-compare-2.pdf){#fig:layout-compare-2}

Суммарный размер интерфейсных таблиц в сравнении с существующими подходами, kB
</div>

\pagebreak
# ДАЛЬНЕЙШЕЕ РАЗВИТИЕ {#sec:heuristics}

В главе [-@sec:imt-imt] представлена одна из возможных эвристик совмещения интерфейсных таблиц, которая ради неувеличения размера IMT добавляет в нее только не пересекающиеся по методам IMT суперинтерфейсов. В Marmot (см. главу [-@sec:previous-work]) была предложена другая эвристика, которая добавляет все IMT суперинтерфейсов, тем самым уменьшая количество таблиц, которые необходимо создавать, но увеличивая размер самих таблиц. На самом деле оба этих подхода являются частными случаями более общей *гибридной* эвристики, выраженной следующим предикатом с параметром $p \in [0,1]$: 
$$
\begin{aligned}
H_p(I,J) := \begin{cases}
  True, & D_I = \varnothing \\
  \frac{|D_I \cap \mathbb{M}_J|}{|D_I|} \leq p, & \text{\it иначе}
  \end{cases},
\end{aligned}
$$
\noindent где $D_I = dom(vnum_I)$. 

Гибридная эвристика определяет, таблицы каких суперинтерфейсов нужно добавлять во время построения раскладки интерфейса I, как показано в листинге [-@lst:imt-hybrid-layout]. Параметр $p$ задает отношение количества уже добавленных методов интерфейса J (которым уже назначены виртуальные номера в I) к количеству всех добавленных методов на текущий момент.


\noindent\Begin{minipage}{\linewidth}

```{#lst:imt-hybrid-layout .numberLines startFrom=5}
// 1. Добавление$\ $наибольших$\ $по$\ $включению IMT в$\ $порядке$\ $убывания$\ $их$\ $размеров
Max := {M $\in$ $\(\mathbb{I}\)\textsubscript{I}$ | $\forall$J $\in$ $\(\mathbb{I}\)\textsubscript{I}$ : M $\notin$ dom(inum$\textsubscript{J}$)}
for (J $\in$ Max ordered by (-$size\textsubscript{J}$) if J $\notin$ dom(inum$\textsubscript{I}$) && H$\textsubscript{p}$(I,J)) {
  addIMT$\textsubscript{I}$(size$\textsubscript{I}$, J)
  size$\textsubscript{I}$ := size$\textsubscript{I}$ + size$\textsubscript{J}$
}
```
: Модификация `buildIMTLayout(I)` с использованием гибридной эвристики

\End{minipage}

Таким образом эвристика описанная в главе [-@sec:imt-imt] и эвристика Marmot эквивалентны гибридной эвристике с параметрами $p = 0$ и $p = 1$, соответственно. Заметим, что оба подхода могут получить результат как лучше, так и хуже другого, что продемонстрировано на рис. [-@fig:layout-considerations-b] и [-@fig:layout-considerations-c] для иерархии из листинга [-@lst:hierarchy-2]. Из чего следует, что перспективным направлением дальнейшей работы является изучение промежуточных значений параметра $p$ рассмотренной гибридной эвристики, которое может улучшить полученные результаты сжатия суммарного размера таблиц. 

\noindent\Begin{minipage}{\linewidth}

```{#lst:hierarchy-2}
L := Interface($\varnothing$,       { "a()", "d()" })
K := Interface($\varnothing$,       { "a()", "b()", "c()" })
J := Interface({L},     { "e()" })
I := Interface({K,L},   { "a()" })
B := Class(null, {I,J}, { "a()" })
C := Class(null, {I},   $\varnothing$)
```
: Пример иерархии

\End{minipage}

\begin{tikzfigure}{fig:hierarchy-2}{Графическое изображение иерархии в листинге~\ref{lst:hierarchy-2}}{}

    \matrix [row sep=1.5em, column sep=1.5em] {
    \node [interface] (L) {L}; & \node [interface] (K) {K}; \\
    \node [interface] (J) {J}; & \node [interface] (I) {I}; \\
    \node [class] (B) {B}; & \node [class] (C) {C}; \\
    };
    \graph [use existing nodes] {
        B -> {J -> L, I -> {L, K}};
        C -> I
    };

\end{tikzfigure}

<!-- <div id="fig:layout-considerations-b">
![p = 0](images/layout-combined-b.pdf){#fig:layout-combined-b}
![p = 1](images/layout-marmot-b.pdf){#fig:layout-marmot-b}

Таблица класса B для краевых значений гибридной эвристики
</div> -->

\begin{figure}

\centering

\subfloat[p = 0]{

\begin{tikzpicture}

    \begin{struct}{vmt B}
        \header                 {vmt B header} {VMT\textsubscript{B}}
        \field [dotted]      {} {vmt B 0}      {\&B::a()}
        \field [dotted]      {} {vmt B 1}      {\&K::b()}
        \field [dashed]      {} {vmt B 2}      {\&K::c()}
        \field [dashed]      {} {vmt B 3}      {\&L::d()}
        \field [dotted]      {} {vmt B 4}      {\&B::a()}
        \field [dashed]      {} {vmt B 5}      {\&L::d()}
        \field               {} {vmt B 6}      {\&J::e()}
    \end{struct}

    \begin{scope}[node distance=0]
    \node [field] (padding) [below=of vmt B 6] {};
    \end{scope}


    \imtL{vmt B 0}{vmt B 2}{IMT\textsubscript{K,B}}
    \imtR{vmt B 0}{vmt B 3}{IMT\textsubscript{I,B}}
    \imtL{vmt B 4}{vmt B 5}{IMT\textsubscript{L,B}}
    \imtR{vmt B 4}{vmt B 6}{IMT\textsubscript{J,B}}

\end{tikzpicture}

\label{fig:layout-combined-b}

}%
\qquad
\subfloat[p = 1]{

\begin{tikzpicture}

    \begin{struct}{vmt B}
        \header                 {vmt B header} {VMT\textsubscript{B}}
        \field [dotted]      {} {vmt B 0}      {\&B::a()}
        \field [dotted]      {} {vmt B 1}      {\&K::b()}
        \field [dashed]      {} {vmt B 2}      {\&K::c()}
        \field [dotted]      {} {vmt B 3}      {\&B::a()}
        \field [dashed]      {} {vmt B 4}      {\&L::d()}
        \field [dotted]      {} {vmt B 5}      {\&B::a()}
        \field [dotted]      {} {vmt B 6}      {\&L::d()}
        \field               {} {vmt B 7}      {\&J::e()}
    \end{struct}


    \imtL{vmt B 0}{vmt B 2}{IMT\textsubscript{K,B}}
    \imtR{vmt B 0}{vmt B 4}{IMT\textsubscript{I,B}}
    \imtL{vmt B 3}{vmt B 4}{IMT\textsubscript{L,B}}
    \imtL{vmt B 5}{vmt B 6}{IMT\textsubscript{L,B}}
    \imtR{vmt B 5}{vmt B 7}{IMT\textsubscript{J,B}}

\end{tikzpicture}

\label{fig:layout-marmot-b}

}%

\caption{Таблица класса B для краевых значений гибридной эвристики}%
    
\label{fig:layout-considerations-b}

\end{figure}

<!-- <div id="fig:layout-considerations-c">
![p = 0](images/layout-combined-c.pdf){#fig:layout-combined-c}
![p = 1](images/layout-marmot-c.pdf){#fig:layout-marmot-c}

Таблица класса C для краевых значений гибридной эвристики
</div> -->

\begin{figure}

\centering

\subfloat[p = 0]{

\begin{tikzpicture}

    \begin{struct}{vmt C}
        \header                 {vmt C header} {VMT\textsubscript{C}}
        \field [dotted]      {} {vmt C 0}      {\&I::a()}
        \field [dotted]      {} {vmt C 1}      {\&K::b()}
        \field [dashed]      {} {vmt C 2}      {\&K::c()}
        \field [dashed]      {} {vmt C 3}      {\&L::d()}
        \field [dotted]      {} {vmt C 4}      {\&I::a()}
        \field               {} {vmt C 5}      {\&L::d()}
    \end{struct}


    \imtL{vmt C 0}{vmt C 2}{IMT\textsubscript{K,C}}
    \imtR{vmt C 0}{vmt C 3}{IMT\textsubscript{I,C}}
    \imtL{vmt C 4}{vmt C 5}{IMT\textsubscript{L,C}}

\end{tikzpicture}

\label{fig:layout-combined-c}

}%
\qquad
\subfloat[p = 1]{

\begin{tikzpicture}

    \begin{struct}{vmt C}
        \header                 {vmt C header} {VMT\textsubscript{C}}
        \field [dotted]      {} {vmt C 0}      {\&I::a()}
        \field [dotted]      {} {vmt C 1}      {\&K::b()}
        \field [dashed]      {} {vmt C 2}      {\&K::c()}
        \field [dotted]      {} {vmt C 3}      {\&I::a()}
        \field               {} {vmt C 4}      {\&L::d()}
    \end{struct}

    \begin{scope}[node distance=0]
    \node [field] (padding) [below=of vmt C 4] {};
    \end{scope}


    \imtL{vmt C 0}{vmt C 2}{IMT\textsubscript{K,C}}
    \imtR{vmt C 0}{vmt C 4}{IMT\textsubscript{I,C}}
    \imtL{vmt C 3}{vmt C 4}{IMT\textsubscript{L,C}}

\end{tikzpicture}

\label{fig:layout-marmot-c}

}%

\caption{Таблица класса C для краевых значений гибридной эвристики}%
    
\label{fig:layout-considerations-c}

\end{figure}

\clearpage
\pagebreak
# ЗАКЛЮЧЕНИЕ {#sec:conclusion -}

Основные результаты работы:

* Разработан алгоритм раскладки таблиц интерфейсных методов, для произвольного языка с ограниченным множественным наследованием;
* Доказана корректность новой раскладки и оценка суммарного размера таблиц сверху суммарным размером таблиц базовой раскладки;
* Алгоритм был реализован в виртуальной машине Excelsior RVM и апробирован на представительном наборе приложений;
* Получено значительное уменьшение суммарного размера таблиц по сравнению с базовой реализацией и с существующими решениями.

Направление дальнейших работ:

* Анализ сложности алгоритма построения раскладки и его доработка для применения в JIT компиляторе, где важна скорость построения таблиц;
* Анализ и доработка эвристик, представленных в главах [-@sec:last-imt] и [-@sec:heuristics];
* Разработка новых оптимизаций в Excelsior RVM, эксплуатирующих полученную вложенную структуру таблиц.

\pagebreak
# ПУБЛИКАЦИИ {-}

1. Трепаков И. С. Эффективная реализация таблиц виртуальных методов в языках с поддержкой ограниченного множественного наследования. // Материалы 56-й Международной научной студенческой конференции МНСК-2018: Математика. Новосиб. гос. ун-т. Новосибирск, 2018.

\pagebreak
# ЛИТЕРАТУРА {-}

<div id="refs"></div>

\renewcommand{\thesection}{П}
\setcounter{subsection}{0}
\setcounter{figure}{0}
\setcounter{lstlisting}{0}
\pagebreak
# ПРИЛОЖЕНИЕ {#sec:appendix -}

Представленный в данной работе алгоритм построения совмещенной раскладки таблиц можно применить к широкому классу языков с ограниченным множественным наследованием, за счет абстрагирования от специфики конкретного языка с помощью абстрактного понятия *метода* и процедуры `resolve`, в которой заключена логика поиска реализации для метода. В данном разделе представлены особенности реализации предложенного подхода для языка Java, а также исходный код прототипной реализации, написанной на Scala.

Везде далее термин *метод* будет употребляться в классическом смысле функции, объявленной в некотором типе. По умолчанию в Java метод может быть переопределен в наследниках класса или интерфейса, в котором этот метод объявлен. Такие методы, объявленные в классе будем называть *виртуальными*, а объявленные в интерфейсе --- *интефрейсными*.

## Область видимости методов в Java

По умолчанию виртуальные методы доступны или видны только из классов и интерфейсов, находящихся в том же пакете, что и объявляющий класс (будем называть такой доступ `package-private`), а интерфейсные методы являются публичными и доступны из любого типа. Как и во многих объектно-ориентированных языках, в Java можно задавать область видимости виртуальных методов. Осуществляется это с помощью модификаторов:

* `public` --- *публичный* метод, доступен из любого класса или интерфейса;
* `protected` --- метод доступен только в объявляющем классе и его наследниках;
* `private` --- *приватный* метод, доступен только из класса, в котором он объявлен.

Так как приватные методы не доступны в наследниках, то для них компилятор всегда может сгенерировать прямой вызов. Остальные методы могут быть переопределены[^override] и, следовательно, вызываются виртуально или интерфейсно.

[^override]: Рассмотрение модификаторов `static` и `final` опущено для упрощения изложения.

Нетривиальная ситуация возникает, когда класс A и наследник B находятся в разных пакетах, и определяют `package-private` метод с одинаковой сигнатурой. В таком случае виртуальные вызовы от переменной формального типа A, в соответствии со спецификацией языка Java, должны вызывать реализацию из метода A, даже если в точку вызова пришел объект типа B, а виртуальные вызовы от переменной формального типа B должны вызывать реализацию типа B. Если в качестве ссылки на метод использовать только имя и сигнатуру, то оба метода получат одинаковый виртуальный номер, и для определения реализации придется использовать дополнительную процедуру-переходник, которая определяет реализацию метода динамически. 

Альтернативно, можно избежать дополнительной косвенности, если заводить отдельные ячейки в VMT под `package-private` методы. Один из способов этого добиться, заключается в том, чтобы различать ссылки на `package-private` методы и на обычные методы, для того чтобы им назначались различные виртуальные номера. В реализации, которая представлена далее в листинге [-@lst:java-layout], для такого разделения введен специальный тип ссылки `PackagePrivateMethod`, который отличается от обычной ссылки `NormalMethod` наличием имени пакета, в котором был объявлен `package-private` метод. 

## Полиморфные вызовы в Java

Для осуществления виртуальных и интерфейсных вызовов в Java байткоде используются инструкции `invokevirtual` и `invokeinterface`, которые различаются не только формальным типом переменной вызова, но еще и степенью статической и динамической верификации в соответствии со спецификацией JVM [@lindholm2014java]: для `invokevirtual` гарантируется совместимость формального типа переменной вызова и настоящего типа объекта во время исполнения, но для `invokeinterface` такой гарантии нет, и проверка соответствия типа должна произойти во время исполнения с выбрасыванием исключения `IncompatibleClassChangeError`, если проверка провалилась. В реализации, использующей таблицы интерфейсных методов, эту проверку можно совместить с поиском таблицы нужного интерфейса и породить исключение, если таблица не найдена.

Помимо проверки совместимости типов, выбрасыванием исключения может закончиться и сам вызов в случаях, когда вызываемая реализация оказалась абстрактной (`AbstractMethodError`), недоступной из формального типа вызова (`IllegalAccessError`) или если вызываемых реализаций оказалось несколько (`IllegalClassChangeError`) при множественном наследовании интерфейсов. Во всех этих случаях при построении таблицы виртуальных или интерфейсных методов вместо адреса на реализацию метода в таблицу вставляется адрес процедуры, которая выбрасывает соответствующее исключение.

Единственная проблема при реализации совмещенной раскладки возникает, если при совмещении ячеек VMT и IMT, вызываются разные реализации в зависимости от того, виртуально или интерфейсно был позван соответствующий метод. Например, в Java виртуальный метод, объявленный как `protected` в классе C, может быть вызван виртуально в любом наследнике этого класса, но при интерфейсном вызове этого метода должно выброситься исключение `IllegalAccessError`, потому что `protected` метод нельзя позвать интерфейсно[^iae-case]. В такой ситуации в конфликтующую ячейку можно вставлять адрес специальной процедуры, которая по мета-информации о вызове, доступной во время исполнения [@йорх2016эффективная], определяет тип вызова и вызывает нужную реализацию.

[^iae-case]: Такая ситуация может произойти при раздельной компиляции класса C и его наследника, если наследник реализующий интерфейс, был скомпилирован до того, как в классе C данный метод стал `protected`.

\pagebreak
## Реализация

В листинге [-@lst:java-layout] представлена реализация построения совмещенной раскладки таблиц для языка Java, написанная на Scala.

```{=latex}
\lstinputlisting[
    language=scala,
    basicstyle=\linespread{1}\small\ttfamily,
    breaklines=true,
    columns=space-flexible,
    numbers=left,
    label={lst:java-layout},
    caption={Реализация совмещенной раскладки для Java},
    ]{src/LayoutBuilder.scala}
```

<!-- 
```{#lst:java-layout-defs include=LayoutBuilder.scala .numberLines startLine=1 endLine=22}
```
: Определение иерархии

```{#lst:java-layout-misc include=LayoutBuilder.scala .numberLines startLine=24 endLine=39}
```
: Вспомогательные определения

```{#lst:java-layout-vmt include=LayoutBuilder.scala .numberLines startLine=41 endLine=82}
```
: Построение раскладки VMT

```{#lst:java-layout-imt include=LayoutBuilder.scala .numberLines startLine=84 endLine=100}
```
: Построение раскладки IMT
 -->

<!-- написать реализацию для Java, описать тонкости спеки (resolve, package private итп.) -->
